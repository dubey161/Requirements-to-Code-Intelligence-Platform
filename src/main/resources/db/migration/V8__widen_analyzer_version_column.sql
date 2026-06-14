-- Increase analyzer_version length to support fallback chain version strings
-- e.g. "llm-llama-3.3-70b-versatile-v1+fallback(rule-based-v1)" = 55 chars
ALTER TABLE requirement_analysis ALTER COLUMN analyzer_version TYPE VARCHAR(100);
