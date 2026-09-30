package com.yunovan.aiadvent.day23;

import java.util.List;

public record Day23RewriteResponse(
        String original,
        String rewritten,
        boolean applied,
        List<String> expansions) {
}