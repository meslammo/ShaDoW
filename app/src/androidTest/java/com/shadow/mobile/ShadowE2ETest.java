package com.shadow.mobile;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ShadowE2ETest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    @Test public void mainSurfaceStreamsThroughCloudFixture(){
        onView(withText("SHADOW")).check(matches(isDisplayed()));
        onView(withHint("Message SHADOW")).perform(typeText("E2E ping"),closeSoftKeyboard());
        onView(withText("➤")).perform(click());
        long deadline=System.currentTimeMillis()+15000L; AssertionError last=null;
        while(System.currentTimeMillis()<deadline){
            try{onView(withText("E2E OK from cloud fixture")).check(matches(isDisplayed())); return;}
            catch(AssertionError e){last=e;try{Thread.sleep(150L);}catch(InterruptedException x){Thread.currentThread().interrupt();break;}}
        }
        if(last!=null)throw last;
        throw new AssertionError("Timed out waiting for SHADOW cloud response");
    }
}
