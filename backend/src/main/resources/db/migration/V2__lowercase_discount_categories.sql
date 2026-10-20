-- V2: discount categories are now stored lower-case (DiscountRequest) so the quote query can compare the raw
-- column and use idx_discounts_category. Lower-case rows written before that change.
UPDATE discounts
SET category = lower(category)
WHERE category IS NOT NULL
  AND category <> lower(category);
