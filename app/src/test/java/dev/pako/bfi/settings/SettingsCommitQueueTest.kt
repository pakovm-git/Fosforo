package dev.pako.bfi.settings

import dev.pako.bfi.model.*
import org.junit.Assert.*
import org.junit.Test

class SettingsCommitQueueTest {
    private fun queue()=SettingsCommitQueue().apply { load(BfiSettings()) }
    @Test fun rapidEditsToDifferentFieldsSurviveOutstandingWrite() {
        val queue=queue()
        queue.enqueue(queue.latest!!.copy(globalPattern=BfiPattern(2,1)),{})
        queue.begin()
        queue.enqueue(queue.latest!!.copy(includedPackages=setOf("game.one")),{})
        queue.enqueue(queue.latest!!.copy(activationMode=ActivationMode.SELECTED_APPS),{})
        assertEquals(BfiPattern(),queue.committed!!.globalPattern)
        queue.complete(true)
        assertEquals(ActivationMode.ALL_APPS,queue.committed!!.activationMode)
        queue.begin(); queue.complete(true)
        queue.begin(); queue.complete(true)
        assertEquals(BfiPattern(2,1),queue.committed!!.globalPattern)
        assertEquals(setOf("game.one"),queue.committed!!.includedPackages)
        assertEquals(ActivationMode.SELECTED_APPS,queue.committed!!.activationMode)
        assertEquals(queue.committed,queue.latest)
    }
    @Test fun intermediateCommitCannotOverwriteNewerDraft() {
        val queue=queue()
        queue.enqueue(queue.latest!!.copy(lastTestSeconds=4),{}); queue.begin()
        queue.enqueue(queue.latest!!.copy(lastTestSeconds=45),{})
        queue.complete(true)
        assertEquals(4,queue.committed!!.lastTestSeconds)
        assertEquals(45,queue.latest!!.lastTestSeconds)
    }
    @Test fun failedFinalWriteRestoresLastDurableValues() {
        val queue=queue()
        queue.enqueue(queue.latest!!.copy(lastTestSeconds=45),{}); queue.begin(); queue.complete(true)
        queue.enqueue(queue.latest!!.copy(lastTestSeconds=450),{}); queue.begin(); queue.complete(false)
        assertEquals(45,queue.latest!!.lastTestSeconds)
        assertFalse(queue.hasPending)
    }
    @Test fun failureCannotDiscardLaterEditsOrAcknowledgeThemEarly() {
        val queue=queue(); val results=mutableListOf<Boolean>()
        queue.enqueue(queue.latest!!.copy(globalPattern=BfiPattern(3,1)),{results.add(it)}); queue.begin()
        queue.enqueue(queue.latest!!.copy(requestRefresh=true),{results.add(it)})
        queue.complete(false).completed(false)
        assertEquals(listOf(false),results)
        assertEquals(BfiPattern(3,1),queue.latest!!.globalPattern)
        queue.begin(); queue.complete(true).completed(true)
        assertEquals(listOf(false,true),results)
        assertEquals(BfiPattern(3,1),queue.committed!!.globalPattern)
        assertTrue(queue.committed!!.requestRefresh)
    }
    @Test fun fullRestorationQueuesBehindEarlierAutosaves() {
        val queue=queue()
        queue.enqueue(queue.latest!!.copy(globalPattern=BfiPattern(12,12)),{}); queue.begin()
        queue.enqueue(BfiSettings(),{},reset=true)
        queue.complete(true)
        assertTrue(queue.begin().reset)
        queue.complete(true)
        assertEquals(BfiSettings(),queue.committed)
    }
}
