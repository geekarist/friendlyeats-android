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
package com.google.firebase.example.fireeats

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.auth.AuthUI
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.example.fireeats.adapter.RestaurantAdapter
import com.google.firebase.example.fireeats.viewmodel.MainActivityViewModel
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query

class MainActivity :
    AppCompatActivity(), View.OnClickListener, FilterDialogFragment.FilterListener,
    RestaurantAdapter.OnRestaurantSelectedListener {

    private var mToolbar: Toolbar? = null
    private var mCurrentSearchView: TextView? = null
    private var mCurrentSortByView: TextView? = null
    private var mRestaurantsRecycler: RecyclerView? = null
    private var mEmptyView: ViewGroup? = null

    private lateinit var firestore: FirebaseFirestore
    private lateinit var query: Query

    private var mFilterDialog: FilterDialogFragment? = null
    private var adapter: RestaurantAdapter? = null

    private var mViewModel: MainActivityViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(mToolbar)

        mCurrentSearchView = findViewById(R.id.text_current_search)
        mCurrentSortByView = findViewById(R.id.text_current_sort_by)
        mRestaurantsRecycler = findViewById(R.id.recycler_restaurants)
        mEmptyView = findViewById(R.id.view_empty)

        findViewById<View>(R.id.filter_bar).setOnClickListener(this)
        findViewById<View>(R.id.button_clear_filter).setOnClickListener(this)

        // View model
        mViewModel = ViewModelProviders.of(this).get(MainActivityViewModel::class.java)

        // Enable Firestore logging
        FirebaseFirestore.setLoggingEnabled(true)

        // Initialize Firestore and the main RecyclerView
        initFirestore()
        initRecyclerView()

        // Filter Dialog
        mFilterDialog = FilterDialogFragment()
    }

    private fun initFirestore() {
        firestore = FirebaseFirestore.getInstance()

        query = firestore.collection("restaurants")
            .orderBy("avgRating", Query.Direction.DESCENDING)
            .limit(LIMIT.toLong())
    }

    private fun initRecyclerView() {

        adapter = object : RestaurantAdapter(query, this@MainActivity) {

            override fun onDataChanged() {
                // Show/hide content if the query returns empty.
                if (itemCount == 0) {
                    mRestaurantsRecycler!!.visibility = View.GONE
                    mEmptyView!!.visibility = View.VISIBLE
                } else {
                    mRestaurantsRecycler!!.visibility = View.VISIBLE
                    mEmptyView!!.visibility = View.GONE
                }
            }

            override fun onError(e: FirebaseFirestoreException) {
                // Show a snackbar on errors
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Error: check logs for info.", Snackbar.LENGTH_LONG
                ).show()
            }
        }

        mRestaurantsRecycler!!.layoutManager = LinearLayoutManager(this)
        mRestaurantsRecycler!!.adapter = adapter
    }

    public override fun onStart() {
        super.onStart()

        // Start sign in if necessary
        if (shouldStartSignIn()) {
            startSignIn()
            return
        }

        // Apply filters
        onFilter(mViewModel!!.filters!!)

        // Start listening for Firestore updates
        if (adapter != null) {
            adapter!!.startListening()
        }
    }

    public override fun onStop() {
        super.onStop()
        if (adapter != null) {
            adapter!!.stopListening()
        }
    }

    private fun onAddItemsClicked() {
        mViewModel!!.onAddItemsClicked(this)
    }

    override fun onFilter(filters: Filters) {

        var query: Query = firestore.collection("restaurants")
        if (filters.hasCategory()) query = query.whereEqualTo("category", filters.category)
        if (filters.hasCity()) query = query.whereEqualTo("city", filters.city)
        if (filters.hasPrice()) query = query.whereEqualTo("price", filters.price)
        if (filters.hasSortBy()) query = query.orderBy(filters.sortBy, filters.sortDirection)

        this.query = query
        adapter?.setQuery(query)

        // Set header
        mCurrentSearchView!!.text = Html.fromHtml(filters.getSearchDescription(this))
        mCurrentSortByView!!.text = filters.getOrderDescription(this)

        // Save filters
        mViewModel!!.filters = filters
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add_items -> onAddItemsClicked()
            R.id.menu_sign_out -> {
                AuthUI.getInstance().signOut(this)
                startSignIn()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            mViewModel!!.isSigningIn = false

            if (resultCode != Activity.RESULT_OK && shouldStartSignIn()) {
                startSignIn()
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.filter_bar -> onFilterClicked()
            R.id.button_clear_filter -> onClearFilterClicked()
        }
    }

    fun onFilterClicked() {
        // Show the dialog containing filter options
        mFilterDialog!!.show(supportFragmentManager, FilterDialogFragment.TAG)
    }

    fun onClearFilterClicked() {
        mFilterDialog!!.resetFilters()

        onFilter(Filters.getDefault())
    }

    override fun onRestaurantSelected(restaurant: DocumentSnapshot) {
        // Go to the details page for the selected restaurant
        val intent = Intent(this, RestaurantDetailActivity::class.java)
        intent.putExtra(RestaurantDetailActivity.KEY_RESTAURANT_ID, restaurant.id)

        startActivity(intent)
    }

    private fun shouldStartSignIn(): Boolean {
        return !mViewModel!!.isSigningIn && FirebaseAuth.getInstance().currentUser == null
    }

    private fun startSignIn() {
        // Sign in with FirebaseUI
        val intent = AuthUI.getInstance().createSignInIntentBuilder()
            .setAvailableProviders(listOf(AuthUI.IdpConfig.EmailBuilder().build()))
            .setIsSmartLockEnabled(false)
            .build()

        startActivityForResult(intent, RC_SIGN_IN)
        mViewModel!!.isSigningIn = true
    }

    private fun showTodoToast() {
        Toast.makeText(this, "TODO: Implement", Toast.LENGTH_SHORT).show()
    }

    companion object {

        private val TAG = "MainActivity"

        private val RC_SIGN_IN = 9001

        private val LIMIT = 50
    }
}
