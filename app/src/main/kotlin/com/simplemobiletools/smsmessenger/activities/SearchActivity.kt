package com.simplemobiletools.smsmessenger.activities

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.SearchResultsAdapter
import com.simplemobiletools.smsmessenger.extensions.conversationsDB
import com.simplemobiletools.smsmessenger.extensions.messagesDB
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.THREAD_TITLE
import com.simplemobiletools.smsmessenger.models.SearchResult
import kotlinx.android.synthetic.main.activity_search.*

class SearchActivity : SimpleActivity() {
    private var mIsSearchOpen = false
    private var mLastSearchedText = ""
    private var mSearchMenuItem: MenuItem? = null

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        updateTextColors(search_holder)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_search, menu)
        setupSearch(menu)
        return true
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        mSearchMenuItem = menu.findItem(R.id.search)

        MenuItemCompat.setOnActionExpandListener(mSearchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                mIsSearchOpen = true
                return true
            }

            // this triggers on device rotation too, avoid doing anything
            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                if (mIsSearchOpen) {
                    mIsSearchOpen = false
                    mLastSearchedText = ""
                    finish()
                }
                return true
            }
        })

        mSearchMenuItem?.expandActionView()
        (mSearchMenuItem?.actionView as? SearchView)?.apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (mIsSearchOpen) {
                        mLastSearchedText = newText
                        textChanged(newText)
                    }
                    return true
                }
            })
        }
    }

    private fun textChanged(text: String) {
        search_placeholder_2.beGoneIf(text.length >= 2)
        if (text.length >= 2) {
            ensureBackgroundThread {
                val searchQuery = "%$text%"
                val messages = messagesDB.getMessagesWithText(searchQuery)
                val conversations = conversationsDB.getConversationsWithText(searchQuery)
                if (text == mLastSearchedText) {
                    val searchResults = ArrayList<SearchResult>()
                    conversations.forEach { conversation ->
                        val date = conversation.date.formatDateOrTime(this, true, true)
                        val searchResult = SearchResult(conversation.title, conversation.phoneNumber, date, conversation.threadId)
                        searchResults.add(searchResult)
                    }

                    messages.forEach { message ->
                        var recipient = message.senderName
                        if (recipient.isEmpty() && message.participants.isNotEmpty()) {
                            val participantNames = message.participants.map { it.name }
                            recipient = TextUtils.join(", ", participantNames)
                        }

                        val date = message.date.formatDateOrTime(this, true, true)
                        val searchResult = SearchResult(recipient, message.body, date, message.threadId)
                        searchResults.add(searchResult)
                    }

                    runOnUiThread {
                        search_results_list.beVisibleIf(searchResults.isNotEmpty())
                        search_placeholder.beVisibleIf(searchResults.isEmpty())

                        SearchResultsAdapter(this, searchResults, search_results_list) {
                            Intent(this, ThreadActivity::class.java).apply {
                                putExtra(THREAD_ID, (it as SearchResult).threadId)
                                putExtra(THREAD_TITLE, it.title)
                                startActivity(this)
                            }
                        }.apply {
                            search_results_list.adapter = this
                        }
                    }
                }
            }
        } else {
            search_placeholder.beVisible()
            search_results_list.beGone()
        }
    }
}
