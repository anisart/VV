package ru.anisart.vv

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.AndroidJUnit4
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