# Noon Bilingual Listing AI Workflow v3.2

This rulebook is the source of truth for Nuono product-listing AI drafts. Apply it before using competitor materials.

## Priority

1. Product facts and confirmed user requirements are authoritative.
2. Explicit operator requirements override style defaults when they do not conflict with facts or compliance.
3. These v3.2 rules override competitor wording.
4. Competitor listings are references for keyword coverage, structure, and market language only. Never copy sentences or claims.

## Required Workflow

Return one structured JSON object that covers all ten workflow steps:

1. Input completeness check: identify confirmed facts, missing critical facts, missing optional facts, and facts requiring human confirmation.
2. Product understanding: identify what the product is, target buyer, usage scenarios, category, quantity/spec cues, and core purchase reason.
3. Copy style decision: choose a practical style based on the category. Do not use exaggerated slogans for utilitarian products.
4. English keywords: extract high-intent English keywords from product facts first, then competitor materials.
5. Arabic keywords: produce localized Arabic search terms, not literal translations only.
6. Attribute summary and guardrails: list confirmed attributes, usable selling points, and forbidden/unconfirmed claims.
7. Listing strategy: provide English and Arabic strategy notes separately.
8. English listing: write English title, exactly five bullets, and long description.
9. Arabic listing: write Arabic title, exactly five bullets, and long description.
10. Chinese quality check: score and explain quality before upload.

## No-Fabrication Guardrails

Do not invent or imply unconfirmed:

- material, dimensions, capacity, quantity, weight, load-bearing ability, waterproof level, battery data, voltage, certification, safety claim, age range, BPA-free, antibacterial, medical grade, compatibility models, warranty, origin, official brand authorization, included accessories, or package count
- "premium", "best", "guaranteed", "safe for babies", "food grade", "non-toxic", "waterproof", or similar strong claims unless the input explicitly confirms them

If a useful selling point depends on missing facts, put it in `needsHumanConfirmation` instead of the upload draft.

## Title Rules

- English title target length: 160-220 characters when facts allow it.
- Arabic title should be localized and fact-consistent; it does not need to mirror English word order.
- The first 60 characters should clearly explain product type plus quantity/spec when known.
- Do not include a brand unless the input confirms it.
- Use keywords naturally. Bold markers may appear only in strategy/review notes, never in `noonUploadDraft`.

## Bullet Rules

- Exactly five bullets per language.
- Each bullet should have the shape `【ALL CAPS HEADING】 - benefit and factual detail`.
- Use distinct benefits. Avoid repeating the same feature with different wording.
- Keep bullets practical and conversion-oriented.
- Do not use emojis in `noonUploadDraft` because Noon upload fields should be plain text.

## Long Description Rules

- English long description target length: 1500-2000 characters when enough factual input exists.
- Arabic long description should be localized Arabic, not a sentence-by-sentence translation.
- Do not expose template labels such as "Headline", "Scenario Intro", or "CTA".
- Do not use aggressive calls to action such as "Buy now" or "Order today".

## Gulf Localization

Use realistic Middle East/Gulf shopping and home-life context only when relevant, such as Arabic coffee, dates, Majlis, family hosting, desk setup, ladies-only gym, stroller outing, school supplies, car storage, or small apartment organization.

Avoid mechanical insertion of Saudi Arabia, UAE, Dubai, Riyadh, Gulf, Ramadan, Eid, or similar words unless the product facts or operator requirement make them directly relevant.

## Arabic Rules

- Arabic must be natural ecommerce Arabic for Noon shoppers.
- Keep factual numbers, specifications, quantities, and limitations consistent with English.
- Do not translate brand names unless an official Arabic brand name is provided.
- Avoid awkward literal translations and mixed English-Arabic phrasing unless SKU, brand, or standard technical terms require it.

## Quality Score

Return a Chinese quality check using 100 points:

- Compliance and no-fabrication: 20
- Title and keyword coverage: 20
- Selling-point completeness: 20
- Conversion and human feel: 15
- Middle East localization: 15
- Language quality: 10

Also include upload notes. Markdown bold and review-only emphasis must be removed before Noon upload.

## Output Safety

`noonUploadDraft` must contain clean upload-ready text only:

- no Markdown bold markers
- no emoji
- no Chinese review labels
- no copied competitor sentences
- no unconfirmed claims

The AI result is a draft only. It must not submit, publish, or write to Noon.
