/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.quickstep;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_MASK;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT;

import static com.android.quickstep.TouchConsumer.INTERACTION_QUICK_SCRUB;
import static com.android.quickstep.TouchConsumer.INTERACTION_QUICK_SWITCH;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;
import android.view.Choreographer;
import android.view.MotionEvent;

import com.android.systemui.shared.system.ChoreographerCompat;

import java.util.ArrayList;

/**
 * Helper class for batching input events
 */
@TargetApi(Build.VERSION_CODES.O)
public class MotionEventQueue {

    private static final String TAG = "MotionEventQueue";

    private static final int ACTION_VIRTUAL = ACTION_MASK - 1;

    private static final int ACTION_QUICK_SWITCH =
            ACTION_VIRTUAL | (1 << ACTION_POINTER_INDEX_SHIFT);
    private static final int ACTION_QUICK_SCRUB_START =
            ACTION_VIRTUAL | (2 << ACTION_POINTER_INDEX_SHIFT);
    private static final int ACTION_QUICK_SCRUB_PROGRESS =
            ACTION_VIRTUAL | (3 << ACTION_POINTER_INDEX_SHIFT);
    private static final int ACTION_QUICK_SCRUB_END =
            ACTION_VIRTUAL | (4 << ACTION_POINTER_INDEX_SHIFT);
    private static final int ACTION_RESET =
            ACTION_VIRTUAL | (5 << ACTION_POINTER_INDEX_SHIFT);

    private final EventArray mEmptyArray = new EventArray();
    private final Object mExecutionLock = new Object();

    // We use two arrays and swap the current index when one array is being consumed
    private final EventArray[] mArrays = new EventArray[] {new EventArray(), new EventArray()};
    private int mCurrentIndex = 0;

    private final Runnable mMainFrameCallback = this::frameCallbackForMainChoreographer;
    private final Runnable mInterimFrameCallback = this::frameCallbackForInterimChoreographer;

    private final Choreographer mMainChoreographer;

    private final TouchConsumer mConsumer;

    private Choreographer mInterimChoreographer;
    private Choreographer mCurrentChoreographer;

    private Runnable mCurrentRunnable;

    public MotionEventQueue(Choreographer choreographer, TouchConsumer consumer) {
        mMainChoreographer = choreographer;
        mConsumer = consumer;

        mCurrentChoreographer = mMainChoreographer;
        mCurrentRunnable = mMainFrameCallback;
        setInterimChoreographerLocked(consumer.getIntrimChoreographer(this));
    }

    public void setInterimChoreographer(Choreographer choreographer) {
        synchronized (mExecutionLock) {
            synchronized (mArrays) {
                setInterimChoreographerLocked(choreographer);
                ChoreographerCompat.postInputFrame(mCurrentChoreographer, mCurrentRunnable);
            }
        }
    }

    private void  setInterimChoreographerLocked(Choreographer choreographer) {
        mInterimChoreographer = choreographer;
        if (choreographer == null) {
            mCurrentChoreographer = mMainChoreographer;
            mCurrentRunnable = mMainFrameCallback;
        } else {
            mCurrentChoreographer = mInterimChoreographer;
            mCurrentRunnable = mInterimFrameCallback;
        }
    }

    public void queue(MotionEvent event) {
        mConsumer.preProcessMotionEvent(event);
        queueNoPreProcess(event);
    }

    private void queueNoPreProcess(MotionEvent event) {
        synchronized (mArrays) {
            EventArray array = mArrays[mCurrentIndex];
            if (array.isEmpty()) {
                ChoreographerCompat.postInputFrame(mCurrentChoreographer, mCurrentRunnable);
            }

            int eventAction = event.getAction();
            if (eventAction == ACTION_MOVE && array.lastEventAction == ACTION_MOVE) {
                // Replace and recycle the last event
                array.set(array.size() - 1, event).recycle();
            } else {
                array.add(event);
                array.lastEventAction = eventAction;
            }
        }
    }

    private void frameCallbackForMainChoreographer() {
        runFor(mMainChoreographer);
    }

    private void frameCallbackForInterimChoreographer() {
        runFor(mInterimChoreographer);
    }

    private void runFor(Choreographer caller) {
        synchronized (mExecutionLock) {
            EventArray array = swapAndGetCurrentArray(caller);
            int size = array.size();
            for (int i = 0; i < size; i++) {
                MotionEvent event = array.get(i);
                if (event.getActionMasked() == ACTION_VIRTUAL) {
                    switch (event.getAction()) {
                        case ACTION_QUICK_SWITCH:
                            mConsumer.updateTouchTracking(INTERACTION_QUICK_SWITCH);
                            break;
                        case ACTION_QUICK_SCRUB_START:
                            mConsumer.updateTouchTracking(INTERACTION_QUICK_SCRUB);
                            break;
                        case ACTION_QUICK_SCRUB_PROGRESS:
                            mConsumer.onQuickScrubProgress(event.getX());
                            break;
                        case ACTION_QUICK_SCRUB_END:
                            mConsumer.onQuickScrubEnd();
                            break;
                        case ACTION_RESET:
                            mConsumer.reset();
                            break;
                        default:
                            Log.e(TAG, "Invalid virtual event: " + event.getAction());
                    }
                } else {
                    mConsumer.accept(event);
                }
                event.recycle();
            }
            array.clear();
            array.lastEventAction = ACTION_CANCEL;
        }
    }

    private EventArray swapAndGetCurrentArray(Choreographer caller) {
        synchronized (mArrays) {
            if (caller != mCurrentChoreographer) {
                return mEmptyArray;
            }
            EventArray current = mArrays[mCurrentIndex];
            mCurrentIndex = mCurrentIndex ^ 1;
            return current;
        }
    }

    private void queueVirtualAction(int action, float progress) {
        queueNoPreProcess(MotionEvent.obtain(0, 0, action, progress, 0, 0));
    }

    public void onQuickSwitch() {
        queueVirtualAction(ACTION_QUICK_SWITCH, 0);
    }

    public void onQuickScrubStart() {
        queueVirtualAction(ACTION_QUICK_SCRUB_START, 0);
    }

    public void onQuickScrubProgress(float progress) {
        queueVirtualAction(ACTION_QUICK_SCRUB_PROGRESS, progress);
    }

    public void onQuickScrubEnd() {
        queueVirtualAction(ACTION_QUICK_SCRUB_END, 0);
    }

    public void reset() {
        queueVirtualAction(ACTION_RESET, 0);
    }

    private static class EventArray extends ArrayList<MotionEvent> {

        public int lastEventAction = ACTION_CANCEL;

        public EventArray() {
            super(4);
        }
    }
}