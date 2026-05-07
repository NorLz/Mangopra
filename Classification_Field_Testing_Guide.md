# Classification Field Testing Guide

## Purpose
This guide is for evaluating the **copra classification model** in actual field conditions.

The main evaluation targets are:
- confusion matrix
- overall accuracy
- precision per class
- recall per class
- F1-score per class
- classifier latency per copra

This guide does **not** treat YOLO detection as the main field-testing target.

## Evaluation Design
Use **two levels of evaluation**:

### Primary Evaluation: Per-Crop Mapped Evaluation
This is the main and stronger classifier evaluation.

Use **one classified copra crop** as the evaluation unit.

That means each record in the primary test sheet should represent:
- one actual copra sample
- one known ground-truth grade
- one predicted grade from the app
- one classifier latency value in milliseconds

This is the cleanest setup because the classifier works on each detected copra crop individually.

### Secondary Evaluation: Batch Count-Comparison Evaluation
This is the supporting real-world batch evaluation.

Use **one batch/session** as the evaluation unit.

That means each record in the secondary test sheet should represent:
- one scan or upload batch
- actual count of Grade I, Grade II, and Grade III in that batch
- predicted count of Grade I, Grade II, and Grade III in that batch
- the latency summary shown by the app for that batch:
  - average latency per copra
  - minimum latency
  - maximum latency
  - number of classified copra

## What "Mapped Per Crop" Means
If a single batch image contains multiple copra, each detected crop must be matched to its real physical sample.

Example:
- physical sample at position 1 = Grade I
- physical sample at position 2 = Grade II
- physical sample at position 3 = Grade III

After the app detects and classifies them, you must be able to say:
- crop 1 belongs to position 1
- crop 2 belongs to position 2
- crop 3 belongs to position 3

This one-to-one matching is what allows a true confusion matrix to be computed.

## Ground-Truth Setup
Before testing, prepare real copra samples that are already labeled by the ground-truth basis you will use in the thesis.

Recommended classes:
- `Grade I`
- `Grade II`
- `Grade III`

For **per-crop mapped evaluation**, the batch should be arranged so that each detected crop can still be matched to a known physical sample.

Recommended practical setup:
1. arrange copra in a fixed layout with enough spacing
2. assign a batch ID such as `B01`
3. assign a position ID such as `P01`, `P02`, `P03`
4. record the actual grade of each position before scanning
5. after scanning, match each detected crop to the known position

## Recommended Data Sheet Layout
### Sheet 1: Primary Raw Data (Per-Crop Mapped)
Use the template in [classification_field_test_template.csv](c:/Users/ADMIN/Documents/PJ/Thesis/Mangopra/classification_field_test_template.csv).

Use these columns for the primary evaluation:

| Column | Header |
|---|---|
| A | batch_id |
| B | position_id |
| C | sample_id |
| D | actual_grade |
| E | predicted_grade |
| F | classification_latency_ms |
| G | source_mode |
| H | model_name |
| I | notes |

Example rows:

| batch_id | position_id | sample_id | actual_grade | predicted_grade | classification_latency_ms | source_mode | model_name | notes |
|---|---|---|---:|---:|---|---|---|
| B01 | P01 | C001 | Grade I | Grade I | 34 | SCAN | SqueezeNet Baseline | clear lighting |
| B01 | P02 | C002 | Grade I | Grade II | 38 | SCAN | SqueezeNet Baseline | shadow on crop |
| B01 | P03 | C003 | Grade II | Grade II | 33 | SCAN | SqueezeNet Baseline | normal |
| B02 | P01 | C004 | Grade III | Grade III | 41 | UPLOAD | SqueezeNet Baseline | uploaded image |

### Sheet 2: Secondary Batch Summary
Use this for the supporting batch-level evaluation.

| Column | Header |
|---|---|
| A | batch_id |
| B | source_mode |
| C | model_name |
| D | actual_grade1_count |
| E | actual_grade2_count |
| F | actual_grade3_count |
| G | predicted_grade1_count |
| H | predicted_grade2_count |
| I | predicted_grade3_count |
| J | average_latency_ms_per_copra |
| K | min_latency_ms |
| L | max_latency_ms |
| M | classified_copra_count |
| N | notes |

Example rows:

| batch_id | source_mode | model_name | actual_grade1_count | actual_grade2_count | actual_grade3_count | predicted_grade1_count | predicted_grade2_count | predicted_grade3_count | average_latency_ms_per_copra | min_latency_ms | max_latency_ms | classified_copra_count | notes |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| B01 | SCAN | SqueezeNet Baseline | 3 | 2 | 1 | 4 | 1 | 1 | 36.2 | 31 | 42 | 6 | strong daylight |
| B02 | UPLOAD | SqueezeNet Baseline | 2 | 2 | 2 | 2 | 3 | 1 | 39.5 | 34 | 48 | 6 | uploaded batch image |

## Confusion Matrix Setup
### Sheet 3: Confusion Matrix
Create this table:

| Actual \ Predicted | Grade I | Grade II | Grade III |
|---|---:|---:|---:|
| Grade I |  |  |  |
| Grade II |  |  |  |
| Grade III |  |  |  |

Assume:
- primary raw data is in a sheet named `PrimaryRawData`
- `actual_grade` is column `D`
- `predicted_grade` is column `E`

Put these labels:
- `B1 = Grade I`
- `C1 = Grade II`
- `D1 = Grade III`
- `A2 = Grade I`
- `A3 = Grade II`
- `A4 = Grade III`

Then in `B2`, enter:

```excel
=COUNTIFS(PrimaryRawData!$D:$D,$A2,PrimaryRawData!$E:$E,B$1)
```

Copy that formula across `B2:D4`.

This fills the full confusion matrix.

## Overall Accuracy Formula
Overall accuracy is:

```text
Total correct predictions / Total predictions
```

If your confusion matrix is in `B2:D4`, use:

```excel
=(B2+C3+D4)/SUM(B2:D4)
```

Format the result as a percentage if you want.

## Precision, Recall, and F1-Score Per Class
### Grade I
Precision for Grade I:

```excel
=B2/SUM(B2:B4)
```

Recall for Grade I:

```excel
=B2/SUM(B2:D2)
```

F1-score for Grade I:

```excel
=2*(precision_cell*recall_cell)/(precision_cell+recall_cell)
```

For example, if:
- `Precision_I` is in `B7`
- `Recall_I` is in `B8`

then F1 is:

```excel
=2*(B7*B8)/(B7+B8)
```

### Grade II
Precision for Grade II:

```excel
=C3/SUM(C2:C4)
```

Recall for Grade II:

```excel
=C3/SUM(B3:D3)
```

F1-score for Grade II:

```excel
=2*(precision_cell*recall_cell)/(precision_cell+recall_cell)
```

### Grade III
Precision for Grade III:

```excel
=D4/SUM(D2:D4)
```

Recall for Grade III:

```excel
=D4/SUM(B4:D4)
```

F1-score for Grade III:

```excel
=2*(precision_cell*recall_cell)/(precision_cell+recall_cell)
```

## Latency Analysis
The app now shows classifier latency as:
- average latency per classified copra
- range, which means:
  - minimum latency
  - maximum latency

### Primary latency summary
For the primary evaluation, use the raw latency column from the per-crop mapped sheet.

If latency is in column `F` of `PrimaryRawData`:

Average latency:

```excel
=AVERAGE(PrimaryRawData!F:F)
```

Minimum latency:

```excel
=MIN(PrimaryRawData!F:F)
```

Maximum latency:

```excel
=MAX(PrimaryRawData!F:F)
```

Sample count:

```excel
=COUNT(PrimaryRawData!F:F)
```

### Secondary batch latency summary
For the secondary evaluation, use the batch summary sheet directly.

If:
- average latency is in column `J`
- min latency is in column `K`
- max latency is in column `L`
- classified copra count is in column `M`

then you may report:
- each batch's latency summary as-is
- plus the overall average of the batch averages if desired

Example:

Overall mean of batch averages:

```excel
=AVERAGE(BatchSummary!J:J)
```

Overall minimum batch latency:

```excel
=MIN(BatchSummary!K:K)
```

Overall maximum batch latency:

```excel
=MAX(BatchSummary!L:L)
```

## Recommended Thesis Reporting
### Primary Evaluation Section
Report:
- confusion matrix
- overall accuracy
- precision for Grade I, Grade II, Grade III
- recall for Grade I, Grade II, Grade III
- F1-score for Grade I, Grade II, Grade III

### Primary Latency Section
Report:
- average classification latency per copra
- minimum latency
- maximum latency
- number of classified copra used in the latency measurement

### Secondary Batch Evaluation Section
Report:
- actual Grade I, II, III counts per batch
- predicted Grade I, II, III counts per batch
- absolute count difference per grade
- average latency per copra in batch
- minimum and maximum latency in batch

Optional batch count-difference formulas:

Grade I count difference:

```excel
=ABS(D2-G2)
```

Grade II count difference:

```excel
=ABS(E2-H2)
```

Grade III count difference:

```excel
=ABS(F2-I2)
```

## Suggested Chapter 4 Interpretation
You can describe the method like this:

> Field testing was conducted using ground-truth copra samples belonging to Grade I, Grade II, and Grade III. The primary evaluation was performed at the per-crop level, where each detected and classified copra crop was mapped to a known physical sample with an assigned ground-truth grade. A confusion matrix was then constructed by comparing the actual grade of each sample with the predicted grade produced by the application. From this matrix, overall accuracy, precision, recall, and F1-score were computed. Classification latency was measured in-app at the classifier inference stage for each successfully classified copra crop. The average latency per copra, together with minimum and maximum latency values, was used to summarize runtime performance. As a secondary practical evaluation, batch-level scans were also summarized by comparing the actual and predicted grade counts within each batch and by recording the batch latency summary shown by the application.

## Practical Recommendation
For the thesis, the best workflow is:
1. use the app to collect predictions and latency
2. record the primary mapped results in the per-crop sheet
3. record the batch totals in the secondary batch sheet
4. compute confusion matrix and classifier metrics from the primary sheet
5. report the batch count comparison as supporting practical evaluation

This is acceptable and common in experimental evaluation.
