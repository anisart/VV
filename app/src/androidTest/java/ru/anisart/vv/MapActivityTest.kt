package ru.anisart.vv

import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.action.ViewActions.click
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.matcher.ViewMatchers.*
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapActivityTest {

    val activityRule = ActivityTestRule(MapActivity::class.java, true, false)

    @JvmField
    @Rule
    val ruleChain: RuleChain = RuleChain
            .outerRule(activityRule)

    @Before
    fun startUp() {
        activityRule.launchActivity(null)
    }

    @Test
    fun clickingOnSettings() {
        onView(withId(R.id.settingsButton)).perform(click())

        onView(withText(R.string.sync_settings)).check(matches(isDisplayed()))
        onView(withText(R.string.style_settings)).check(matches(isDisplayed()))
    }

    @Test
    fun hidingFabsOnStyleSettingsShows() {
        onView(withId(R.id.settingsButton)).perform(click())
        onView(withText(R.string.style_settings)).perform(click())

        onView(withId(R.id.settingsButton)).check(matches(not(isDisplayed())))
        onView(withId(R.id.recordButton)).check(matches(not(isDisplayed())))
    }
}