/* Copyright 2026 Scroll Guard contributors. Licensed under the Apache License, Version 2.0. */
package app.scrollguard.services

import app.scrollguard.models.InstagramProtectionMode
import app.scrollguard.services.detectors.InstagramScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramProtectionContinuityTest {
    private val selective = InstagramProtectionMode.SOCIAL

    @Test fun losingAllIdentifiersDoesNotUncoverAnAlreadyGuardedFeed() {
        for (feed in listOf(InstagramScreen.HOME_REEL, InstagramScreen.HOME_MEDIA_UNKNOWN,
            InstagramScreen.REELS, InstagramScreen.EXPLORE)) {
            val policy = InstagramProtectionContinuity()
            policy.resolve(selective, 7, feed, InstagramTreeQuality.COMPLETE)
            for (quality in InstagramTreeQuality.entries) {
                val result = policy.resolve(selective, 7, InstagramScreen.OTHER_ALLOWED, quality)
                assertEquals(feed, result.screen)
                assertTrue(result.retained)
            }
        }
    }

    @Test fun incompleteHomeScanCannotProveAReelHasGone() {
        val policy = InstagramProtectionContinuity()
        policy.resolve(selective, 7, InstagramScreen.HOME_REEL, InstagramTreeQuality.COMPLETE)
        assertEquals(InstagramScreen.HOME_REEL, policy.resolve(selective, 7,
            InstagramScreen.HOME_POSTS, InstagramTreeQuality.INCOMPLETE).screen)
        assertTrue(policy.isGuarded(selective, 7))
    }

    @Test fun confirmedSafeDestinationReleasesTheLatchAndDoesNotCoverItsPhotoPicker() {
        for (safe in listOf(InstagramScreen.MESSAGES, InstagramScreen.PROFILE, InstagramScreen.STORY)) {
            val policy = InstagramProtectionContinuity()
            policy.resolve(selective, 7, InstagramScreen.REELS, InstagramTreeQuality.COMPLETE)
            val safeResult = policy.resolve(selective, 7, safe, InstagramTreeQuality.COMPLETE)
            assertEquals(safe, safeResult.screen)
            assertFalse(safeResult.retained)
            assertFalse(policy.isGuarded(selective, 7))
            assertEquals(InstagramScreen.OTHER_ALLOWED, policy.resolve(selective, 7,
                InstagramScreen.OTHER_ALLOWED, InstagramTreeQuality.COMPLETE).screen)
        }
    }

    @Test fun messageRowGapsAlreadyValidatedByThePolicyDoNotCoverTyping() {
        val policy = InstagramProtectionContinuity()
        policy.resolve(selective, 7, InstagramScreen.REELS, InstagramTreeQuality.COMPLETE)
        assertEquals(InstagramScreen.MESSAGES, policy.resolve(selective, 7,
            InstagramScreen.MESSAGES, InstagramTreeQuality.INCOMPLETE).screen)
        assertFalse(policy.isGuarded(selective, 7))
    }

    @Test fun completeHomePhotoDestinationReleasesTheOldMediaGuard() {
        val policy = InstagramProtectionContinuity()
        policy.resolve(selective, 7, InstagramScreen.HOME_MEDIA_UNKNOWN, InstagramTreeQuality.COMPLETE)
        val result = policy.resolve(selective, 7, InstagramScreen.HOME_POSTS, InstagramTreeQuality.COMPLETE,
            confirmedHomePhoto = true)
        assertEquals(InstagramScreen.HOME_POSTS, result.screen)
        assertFalse(result.retained)
        assertFalse(policy.isGuarded(selective, 7))
    }

    @Test fun returningToVideoAfterAChatStartsANewGuardImmediately() {
        val policy = InstagramProtectionContinuity()
        policy.resolve(selective, 7, InstagramScreen.REELS, InstagramTreeQuality.COMPLETE)
        policy.resolve(selective, 7, InstagramScreen.MESSAGES, InstagramTreeQuality.COMPLETE)
        assertEquals(InstagramScreen.HOME_REEL, policy.resolve(selective, 7,
            InstagramScreen.HOME_REEL, InstagramTreeQuality.COMPLETE).screen)
        assertTrue(policy.isGuarded(selective, 7))
    }

    @Test fun completeHomeHeaderWithoutPositivePhotoCannotReleaseAPlayingFeed() {
        val policy = InstagramProtectionContinuity()
        policy.resolve(selective, 7, InstagramScreen.HOME_REEL, InstagramTreeQuality.COMPLETE)
        val result = policy.resolve(selective, 7, InstagramScreen.HOME_POSTS, InstagramTreeQuality.COMPLETE)
        assertEquals(InstagramScreen.HOME_REEL, result.screen)
        assertTrue(result.retained)
    }

    @Test fun photoEvidenceCannotReleaseAnIncompleteHomeScan() {
        val policy = InstagramProtectionContinuity()
        policy.resolve(selective, 7, InstagramScreen.HOME_REEL, InstagramTreeQuality.COMPLETE)
        val result = policy.resolve(selective, 7, InstagramScreen.HOME_POSTS, InstagramTreeQuality.INCOMPLETE,
            confirmedHomePhoto = true)
        assertEquals(InstagramScreen.HOME_REEL, result.screen)
        assertTrue(result.retained)
    }

    @Test fun anotherWindowCannotInheritAFeedGuard() {
        val policy = InstagramProtectionContinuity()
        policy.resolve(selective, 7, InstagramScreen.REELS, InstagramTreeQuality.COMPLETE)
        val result = policy.resolve(selective, 8, InstagramScreen.OTHER_ALLOWED, InstagramTreeQuality.COMPLETE)
        assertEquals(InstagramScreen.OTHER_ALLOWED, result.screen)
        assertFalse(result.retained)
    }

    @Test fun explicitModeChangeCannotKeepAnOldSelectiveGuard() {
        val policy = InstagramProtectionContinuity()
        policy.resolve(selective, 7, InstagramScreen.REELS, InstagramTreeQuality.COMPLETE)
        assertEquals(InstagramScreen.UNKNOWN, policy.resolve(InstagramProtectionMode.FEED_LOCK, 7,
            InstagramScreen.UNKNOWN, InstagramTreeQuality.MISSING).screen)
        assertFalse(policy.isGuarded(selective, 7))
    }

    @Test fun leavingOrDisablingInstagramClearsContinuity() {
        val policy = InstagramProtectionContinuity()
        policy.resolve(selective, 7, InstagramScreen.REELS, InstagramTreeQuality.COMPLETE)
        policy.reset()
        assertFalse(policy.isGuarded(selective, 7))
        assertEquals(InstagramScreen.OTHER_ALLOWED, policy.resolve(selective, 7,
            InstagramScreen.OTHER_ALLOWED, InstagramTreeQuality.MISSING).screen)
    }

    @Test fun unknownAttachmentBeforeAnyGuardKeepsSelectiveModeUsable() {
        val policy = InstagramProtectionContinuity()
        assertEquals(InstagramScreen.OTHER_ALLOWED, policy.resolve(selective, 7,
            InstagramScreen.OTHER_ALLOWED, InstagramTreeQuality.COMPLETE).screen)
        assertFalse(policy.isGuarded(selective, 7))
    }
}
