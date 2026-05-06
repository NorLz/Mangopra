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

## Evaluation Unit
Use **one classified copra** as the evaluation unit.

That means each record in your test sheet should represent:
- one actual copra sample
- one known ground-truth grade
- one predicted grade from the app
- one classifier latency value in milliseconds

This is the cleanest setup because the classifier works on each detected copra crop individually.

## What To Record During Field Testing
Use the template in [classification_field_test_template.csv](c:/Users/ADMIN/Documents/PJ/Thesis/Mangopra/classification_field_test_template.csv).

For each tested copra sample, record:
- `sample_id`: unique identifier like `C001`
- `session_id`: optional scan batch/session label like `S01`
- `actual_grade`: the true grade assigned by the human ground-truth process
- `predicted_grade`: the grade predicted by the app
- `classification_latency_ms`: the classifier latency shown or logged for that copra
- `source_mode`: `SCAN` or `UPLOAD`
- `model_name`: the classification model used
- `notes`: optional comments such as lighting, blur, partial occlusion, or unusual sample condition

## Ground-Truth Setup
Before testing, prepare real copra samples that are already labeled by the ground-truth basis you will use in the thesis.

Recommended classes:
- `Grade I`
- `Grade II`
- `Grade III`

During field testing:
1. present the known sample to the app
2. let the app classify it
3. record the predicted grade
4. record the latency for that classified copra

## Recommended Data Sheet Layout
### Sheet 1: Raw Data
Use these columns:

| Column | Header |
|---|---|
| A | sample_id |
| B | session_id |
| C | actual_grade |
| D | predicted_grade |
| E | classification_latency_ms |
| F | source_mode |
| G | model_name |
| H | notes |

Example rows:

| sample_id | session_id | actual_grade | predicted_grade | classification_latency_ms | source_mode | model_name | notes |
|---|---|---|---:|---:|---|---|---|
| C001 | S01 | Grade I | Grade I | 34 | SCAN | SqueezeNet Baseline | clear lighting |
| C002 | S01 | Grade I | Grade II | 38 | SCAN | SqueezeNet Baseline | shadow on crop |
| C003 | S01 | Grade II | Grade II | 33 | SCAN | SqueezeNet Baseline | normal |
| C004 | S02 | Grade III | Grade III | 41 | UPLOAD | SqueezeNet Baseline | uploaded image |

## Confusion Matrix Setup
### Sheet 2: Confusion Matrix
Create this table:

| Actual \ Predicted | Grade I | Grade II | Grade III |
|---|---:|---:|---:|
| Grade I |  |  |  |
| Grade II |  |  |  |
| Grade III |  |  |  |

Assume:
- raw data is in a sheet named `RawData`
- `actual_grade` is column `C`
- `predicted_grade` is column `D`

Put these labels:
- `B1 = Grade I`
- `C1 = Grade II`
- `D1 = Grade III`
- `A2 = Grade I`
- `A3 = Grade II`
- `A4 = Grade III`

Then in `B2`, enter:

```excel
=COUNTIFS(RawData!$C:$C,$A2,RawData!$D:$D,B$1)
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

For field-testing summary across all recorded samples, use the raw latency column from `classification_latency_ms`.

If latency is in column `E` of `RawData`:

Average latency:

```excel
=AVERAGE(RawData!E:E)
```

Minimum latency:

```excel
=MIN(RawData!E:E)
```

Maximum latency:

```excel
=MAX(RawData!E:E)
```

Sample count:

```excel
=COUNT(RawData!E:E)
```

## Recommended Thesis Reporting
### Accuracy Section
Report:
- confusion matrix
- overall accuracy
- precision for Grade I, Grade II, Grade III
- recall for Grade I, Grade II, Grade III
- F1-score for Grade I, Grade II, Grade III

### Latency Section
Report:
- average classification latency per copra
- minimum latency
- maximum latency
- number of classified copra used in the latency measurement

## Suggested Chapter 4 Interpretation
You can describe the method like this:

> Field testing was conducted using ground-truth copra samples belonging to Grade I, Grade II, and Grade III. Each sample was scanned using the mobile application, and the predicted class was recorded. A confusion matrix was then constructed by comparing the actual grade of each sample with the predicted grade produced by the application. From this matrix, overall accuracy, precision, recall, and F1-score were computed. Classification latency was measured in-app at the classifier inference stage for each successfully classified copra crop. The average latency per copra, together with minimum and maximum latency values, was used to summarize real-world runtime performance.

## Practical Recommendation
For the thesis, the best workflow is:
1. use the app to collect predictions and latency
2. record the results in the raw data sheet
3. compute confusion matrix and metrics in Excel or Google Sheets

This is acceptable and common in experimental evaluation.
