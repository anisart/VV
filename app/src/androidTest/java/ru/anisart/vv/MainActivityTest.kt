package ru.anisart.vv

import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.matcher.ViewMatchers.isDisplayed
import android.support.test.espresso.matcher.ViewMatchers.withText
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    val activityRule = ActivityTestRule(MainActivity::class.java, true, false)

    @JvmField
    @Rule
    val ruleChain: RuleChain = RuleChain
            .outerRule(activityRule)

    @Before
    fun startUp() {
        activityRule.launchActivity(null)
    }

    @Test
    fun existingOfAllButtons() {
        onView(withText("Select OsmAnd folder")).check(matches(isDisplayed()))
        onView(withText("Update Vw data")).check(matches(isDisplayed()))
        onView(withText("Recreate tiles and rides")).check(matches(isDisplayed()))
    }
}