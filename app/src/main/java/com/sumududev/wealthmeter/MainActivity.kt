package com.sumududev.wealthmeter

import HomeFragment
import ProfileFragment
import StatFragment
import TransactionsFragment
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        bottomNavigationView = findViewById(R.id.bottomNavigationView)

        // Set default fragment when activity is first created
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        // Set up bottom navigation item selection listener
        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_transactions -> {
                    loadFragment(TransactionsFragment())
                    true
                }
                R.id.nav_stats -> {
                    loadFragment(StatFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.flFragment, fragment)
            commit()
        }
    }

    // Optional: Handle back button to navigate between fragments
    override fun onBackPressed() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.flFragment)
        when (currentFragment) {
            is HomeFragment -> super.onBackPressed() // Exit app if already on home
            else -> bottomNavigationView.selectedItemId = R.id.nav_home // Go back to home
        }
    }
}