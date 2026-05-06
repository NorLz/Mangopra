# Pricing QA Checklist

Use this checklist to verify the Android pricing flow end to end after changes.

## Setup

1. Make sure `PRICING_API_BASE_URL` points to the deployed pricing service.
2. Confirm the pricing backend has at least one latest pricing row.
3. Install a fresh debug build on the phone.
4. Start with internet enabled for the first sync tests.

## Online Fetch

1. Open the app.
2. Wait a few seconds, then go to the Home screen.
3. Tap the yellow pricing FAB.
4. Confirm the latest pricing modal shows:
   - Grade I, II, and III prices
   - Effective date
   - Backend recorded time
   - Device saved time
5. Confirm the status chip reads like a fresh sync state such as `Updated just now` or `Updated X minutes ago`.

## Modal Refresh UX

1. Open the latest pricing modal.
2. Tap `Refresh Latest Pricing`.
3. Confirm while loading:
   - button text changes to `Refreshing...`
   - button is disabled
   - small loading indicator is visible
   - helper text explains why refresh is disabled
4. Confirm after success:
   - button is enabled again
   - helper text changes to success wording
   - saved time updates

## Offline Cache Reuse

1. Open the app once while online and confirm pricing is fetched.
2. Turn off Wi-Fi/mobile data.
3. Reopen the app.
4. Open the latest pricing modal.
5. Confirm cached pricing still appears.
6. Confirm the modal does not crash and clearly shows saved pricing details.

## Scan Flow Pricing

1. While pricing is already cached, perform a scan with detectable copra.
2. Open the scan results sheet.
3. Confirm the pricing card shows:
   - estimated price per kg
   - weighted explanation text
   - grade proportions
4. Scroll to the bottom and confirm the pricing card is fully visible.

## Upload Flow Pricing

1. While pricing is already cached, upload an image with detectable copra.
2. Open the upload results modal.
3. Confirm the pricing card shows:
   - estimated price per kg
   - weighted explanation text
   - grade proportions
4. Scroll to the bottom and confirm the pricing card is fully visible.

## Saved History

1. Save at least one scan result and one upload result.
2. Open each item from Home history.
3. Confirm the Saved Analysis header shows:
   - pricing snapshot label
   - estimated price per kg
   - effective date
   - proportions
4. Confirm the Saved Analysis back button matches the yellow style used in Scan and Upload.
5. Confirm the image area bottom border is visible and not cramped by the action buttons.

## History Consistency

1. Save one analysis with the current pricing.
2. Update the backend with a newer pricing row.
3. Reopen Home while online and allow the app to refresh pricing.
4. Save a new analysis.
5. Confirm:
   - old saved analysis keeps its original pricing snapshot
   - new analysis uses the newer pricing

## Visual Consistency

1. Confirm the Home pricing FAB uses the outline tag icon.
2. Confirm the FAB background uses the same warm yellow accent as the app.
3. Confirm the pricing modal header icon matches the FAB icon.
4. Confirm the modal content is scrollable and no bottom content is clipped.

## Failure Handling

1. Disable internet and tap refresh in the pricing modal.
2. Confirm:
   - app stays stable
   - cached pricing remains visible if available
   - helper text explains fallback to saved pricing
3. If there is no cached pricing yet, confirm the modal clearly communicates that no saved pricing is available.
