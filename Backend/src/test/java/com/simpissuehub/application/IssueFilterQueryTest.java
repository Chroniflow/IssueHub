package com.simpissuehub.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IssueFilterQueryTest {
    @Test
    void parsesAllPrefixesAndRemainingFreeText() {
        IssueFilterQuery filter = IssueFilterQuery.parse("崩溃 author:alice tag:bug category:服务器 区块", "viewer");
        assertEquals("崩溃 区块", filter.query());
        assertEquals("alice", filter.author());
        assertEquals("bug", filter.tag());
        assertEquals("服务器", filter.category());
    }

    @Test
    void resolvesMeAuthorToCurrentUsername() {
        IssueFilterQuery filter = IssueFilterQuery.parse("author:me", "viewer");
        assertEquals("viewer", filter.author());
        assertNull(filter.query());
    }

    @Test
    void keepsMeLiteralWhenAnonymous() {
        IssueFilterQuery filter = IssueFilterQuery.parse("author:ME", null);
        assertEquals("ME", filter.author());
    }

    @Test
    void isCaseInsensitiveAndLastPrefixWins() {
        IssueFilterQuery filter = IssueFilterQuery.parse("Author:a TAG:x author:b", "viewer");
        assertEquals("b", filter.author());
        assertEquals("x", filter.tag());
    }

    @Test
    void handlesBlankAndPrefixOnlyInput() {
        assertNull(IssueFilterQuery.parse("   ", "viewer").query());
        IssueFilterQuery filter = IssueFilterQuery.parse("author: tag: 正常", "viewer");
        assertNull(filter.author());
        assertNull(filter.tag());
        assertEquals("正常", filter.query());
    }
}
