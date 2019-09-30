/**
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.example.fireeats.adapter

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.*
import com.google.firebase.firestore.EventListener
import java.util.*

/**
 * RecyclerView adapter for displaying the results of a Firestore [Query].
 *
 * Note that this class forgoes some efficiency to gain simplicity. For example, the result of
 * [DocumentSnapshot.toObject] is not cached so the same object may be deserialized
 * many times as the user scrolls.
 *
 * See the adapter classes in FirebaseUI (https://github.com/firebase/FirebaseUI-Android/tree/master/firestore) for a
 * more efficient implementation of a Firestore RecyclerView Adapter.
 */
abstract class FirestoreAdapter<VH : RecyclerView.ViewHolder>(private var query: Query?) :
    RecyclerView.Adapter<VH>(), EventListener<QuerySnapshot> {
    private var mRegistration: ListenerRegistration? = null

    private val snapshots = ArrayList<DocumentSnapshot>()

    fun startListening() {
        val q = query
        if (q != null && mRegistration == null) {
            mRegistration = q.addSnapshotListener(this)
        }
    }

    override fun onEvent(snapshot: QuerySnapshot?, exception: FirebaseFirestoreException?) {
        exception?.let {
            Log.w(TAG, "onEvent error", Exception(exception))
            return
        }

        snapshot?.let {
            for (change in it.documentChanges) {
                when (change.type) {
                    DocumentChange.Type.ADDED -> onDocumentAdded(change)
                    DocumentChange.Type.MODIFIED -> onDocumentModified(change)
                    DocumentChange.Type.REMOVED -> onDocumentRemoved(change)
                }
            }

            onDataChanged()
        }
    }

    private fun onDocumentAdded(change: DocumentChange) {
        snapshots.add(change.newIndex, change.document)
        notifyItemInserted(change.newIndex)
    }

    private fun onDocumentModified(change: DocumentChange) {
        if (change.oldIndex == change.newIndex) {
            snapshots.set(change.oldIndex, change.document)
            notifyItemChanged(change.oldIndex)
        } else {
            snapshots.removeAt(change.oldIndex)
            snapshots.add(change.newIndex, change.document)
            notifyItemMoved(change.oldIndex, change.newIndex)
        }
    }

    private fun onDocumentRemoved(change: DocumentChange) {
        snapshots.removeAt(change.oldIndex)
        notifyItemRemoved(change.oldIndex)
    }

    fun stopListening() {
        if (mRegistration != null) {
            mRegistration!!.remove()
            mRegistration = null
        }

        snapshots.clear()
        notifyDataSetChanged()
    }

    fun setQuery(query: Query) {
        // Stop listening
        stopListening()

        // Clear existing data
        snapshots.clear()
        notifyDataSetChanged()

        // Listen to new query
        this.query = query
        startListening()
    }

    override fun getItemCount(): Int {
        return snapshots.size
    }

    protected fun getSnapshot(index: Int): DocumentSnapshot {
        return snapshots[index]
    }

    protected open fun onError(e: FirebaseFirestoreException) {}

    protected open fun onDataChanged() {}

    companion object {

        private val TAG = "Firestore Adapter"
    }
}
