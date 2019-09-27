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
package com.google.firebase.example.fireeats.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel

import com.google.firebase.example.fireeats.Filters
import com.google.firebase.example.fireeats.util.RestaurantUtil
import com.google.firebase.firestore.FirebaseFirestore

/**
 * ViewModel for [com.google.firebase.example.fireeats.MainActivity].
 */
class MainActivityViewModel : ViewModel() {

    private val firestore: FirebaseFirestore
    var isSigningIn: Boolean = false
    var filters: Filters? = null

    init {
        isSigningIn = false
        filters = Filters.getDefault()
        firestore = FirebaseFirestore.getInstance()
    }

    fun onAddItemsClicked(context: Context) {
        val restaurantsRef = firestore.collection("restaurants")
        for (i in 0 until 10) {
            val restaurant = RestaurantUtil.getRandom(context)
            restaurantsRef.add(restaurant)
        }
    }
}
