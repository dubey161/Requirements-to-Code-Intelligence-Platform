package com.engineeringproductivity.platform.requirement.analysis.application;

import com.engineeringproductivity.platform.requirement.analysis.domain.model.RequirementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Classifies a requirement into a RequirementType BEFORE analysis and code generation.
 *
 * This is the key architectural improvement: knowing the type upfront routes the
 * requirement to the correct generator strategy (AlgorithmGenerator vs CrudGenerator etc.)
 * instead of blindly running "extract nouns → generate CRUD entities" for everything.
 *
 * Uses fast keyword heuristics (no LLM call). Runs in < 1ms.
 */
@Component
public class RequirementClassifier {

    private static final Logger log = LoggerFactory.getLogger(RequirementClassifier.class);

    // ── Algorithm patterns ────────────────────────────────────────────────────
    // Matches: "Write a Java program", "main method", Input:/Output:/Constraints: format,
    // LeetCode keywords, array/string algorithm problems, coding challenge phrasing
    private static final Pattern ALGORITHM = Pattern.compile(
            "write a java (program|class|solution|code)|" +
            "public static void main|\\bmain method\\b|" +
            "\\binput\\s*:.*\\boutput\\s*:|\\boutput\\s*:.*\\binput\\s*:|" +  // Input: ... Output:
            "\\bconstraints?\\s*:|\\bexample\\s*\\d+\\s*:|" +                  // Constraints: Example 1:
            "\\bleetcode\\b|\\bhackerrank\\b|\\bcodechef\\b|\\binterviewbit\\b|" +
            "\\btwo sum\\b|\\bfibonacci\\b|\\bbinary search\\b|" +
            "dynamic programming|sliding window|two pointer|" +
            "\\b(bfs|dfs)\\b|\\bgraph traversal\\b|\\btree traversal\\b|" +
            "reverse (string|array|number|words)|" +
            "\\bpalindrome\\b|\\banagram\\b|\\bsubsequence\\b|\\bsubarray\\b|" +
            "\\bwords\\s*\\[\\]|\\bweights\\s*\\[\\]|\\bstrings\\s*\\[\\]|\\bnums\\s*\\[\\]|" +
            "weighted.*mapping|character.*mapping|letter.*mapping|" +
            "\\bsolve\\s+this\\b|given (an?|the) (array|string|number|list) .*find|" +
            "time complexity|space complexity|big.?o notation",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // ── Event-driven patterns ─────────────────────────────────────────────────
    private static final Pattern EVENT_DRIVEN = Pattern.compile(
            "kafka|rabbitmq|activemq|pulsar|" +
            "event.?driven|message.?queue|message.?broker|" +
            "\\bconsumer\\b|\\bproducer\\b|\\btopic\\b|\\bpartition\\b|" +
            "event.*stream|stream.*process",
            Pattern.CASE_INSENSITIVE
    );

    // ── Batch job patterns ────────────────────────────────────────────────────
    private static final Pattern BATCH_JOB = Pattern.compile(
            "\\bbatch\\b|scheduled (job|task|process)|\\bcron\\b|" +
            "process (csv|excel|xml|file)|import (csv|excel|data)|etl pipeline|" +
            "\\b@scheduled\\b|spring batch",
            Pattern.CASE_INSENSITIVE
    );

    // ── CLI patterns ──────────────────────────────────────────────────────────
    private static final Pattern CLI = Pattern.compile(
            "command.?line (tool|application|program)|\\bcli\\b|" +
            "\\bargs\\s*\\[\\]\\b|command.*argument|terminal.*tool",
            Pattern.CASE_INSENSITIVE
    );

    // ── Library/utility patterns ──────────────────────────────────────────────
    private static final Pattern LIBRARY = Pattern.compile(
            "utility class|helper class|\\blibrary\\b|\\bsdk\\b|" +
            "no (controller|endpoint|database|rest api)|" +
            "reusable (component|module|util)",
            Pattern.CASE_INSENSITIVE
    );

    public RequirementType classify(String title, String description, List<String> acceptanceCriteria) {
        String combined = title + "\n" + description + "\n" + String.join("\n", acceptanceCriteria);

        RequirementType type;

        if (ALGORITHM.matcher(combined).find()) {
            type = RequirementType.ALGORITHM;
        } else if (EVENT_DRIVEN.matcher(combined).find()) {
            type = RequirementType.MICROSERVICE;
        } else if (BATCH_JOB.matcher(combined).find()) {
            type = RequirementType.BATCH_JOB;
        } else if (CLI.matcher(combined).find()) {
            type = RequirementType.CLI_APPLICATION;
        } else if (LIBRARY.matcher(combined).find()) {
            type = RequirementType.LIBRARY;
        } else {
            type = RequirementType.SPRING_CRUD; // default — generate standard CRUD application
        }

        log.info("Classified requirement '{}' as {}", title, type);
        return type;
    }
}
