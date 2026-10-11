package dev.pako.bfi.rules

import dev.pako.bfi.model.*
import dev.pako.bfi.state.Reason
import org.junit.Assert.*
import org.junit.Test

class AppRulesTest {
    @Test fun unrestrictedAllAppsNeedsNoIdentity() { assertFalse(BfiSettings().needsAppIdentity); assertEquals(BfiPattern(),AppRuleResolver.resolve(BfiSettings(),null).pattern) }
    @Test fun exclusionWinsOverInclusionAndOverride() {
        val s=BfiSettings(activationMode=ActivationMode.SELECTED_APPS,includedPackages=setOf("pkg"),excludedPackages=setOf("pkg"),overrides=mapOf("pkg" to BfiPattern(3,2)))
        assertNull(AppRuleResolver.resolve(s,"pkg").pattern)
    }
    @Test fun unknownAndEmptySelectedFailClosed() {
        assertNull(AppRuleResolver.resolve(BfiSettings(activationMode=ActivationMode.SELECTED_APPS),"pkg").pattern)
        assertEquals(Reason.UNKNOWN_APP,AppRuleResolver.resolve(BfiSettings(excludedPackages=setOf("other")),null).reason)
    }
    @Test fun eligibleOverrideOtherwiseUsesGlobal() {
        val s=BfiSettings(globalPattern=BfiPattern(2,1),overrides=mapOf("pkg" to BfiPattern(3,2)))
        assertEquals(BfiPattern(3,2),AppRuleResolver.resolve(s,"pkg").pattern); assertEquals(s.globalPattern,AppRuleResolver.resolve(s,"other").pattern)
    }
    @Test fun eventPauseDoesNotKeepAStalePackage() {
        val r=ForegroundEventResolver(); r.consume(listOf(AppEvent(1,"pkg","A",true))); assertEquals("pkg",r.current())
        r.consume(emptyList()); assertEquals("pkg",r.current())
        r.consume(listOf(AppEvent(2,"pkg","A",false))); assertNull(r.current())
    }
    @Test fun twoActivitiesOfOnePackageAreNotTwoApps() {
        val r=ForegroundEventResolver();r.consume(listOf(AppEvent(1,"pkg","A",true),AppEvent(2,"pkg","B",true),AppEvent(3,"pkg","A",false)))
        assertEquals("pkg",r.current())
    }
    @Test fun multiwindowIsUnknownUntilOneAppPauses() {
        val r=ForegroundEventResolver();r.consume(listOf(AppEvent(1,"pkg","A",true),AppEvent(2,"other","B",true)))
        assertNull(r.current()); r.consume(listOf(AppEvent(3,"other","B",false))); assertEquals("pkg",r.current())
    }
    @Test fun resetAndEventSortingDoNotReuseOldIdentity() {
        val r=ForegroundEventResolver();r.consume(listOf(AppEvent(2,"pkg","A",false),AppEvent(1,"pkg","A",true))); assertNull(r.current())
        r.consume(listOf(AppEvent(3,"pkg","A",true)));r.clear(); assertNull(r.current())
    }
}
