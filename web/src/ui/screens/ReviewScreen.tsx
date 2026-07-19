import { useState } from 'react';
import type { FieldKey, FieldResult, ReviewRow } from '../../types/domain';
import { buildCustomerField, buildDaysRemainingField, buildProjectNumberField, isFieldOk } from '../../core/ocr/validation';

interface Props {
  rows: ReviewRow[];
  confidenceThreshold: number;
  onChange: (rows: ReviewRow[]) => void;
  onProceed: () => void;
  onBack: () => void;
}

const FIELD_BUILDERS: Record<FieldKey, (text: string, confidence: number) => FieldResult> = {
  projectNumber: buildProjectNumberField,
  customer: buildCustomerField,
  daysRemaining: buildDaysRemainingField,
};

function recomputeNeedsReview(row: ReviewRow, threshold: number): ReviewRow {
  const needsReview =
    !isFieldOk(row.projectNumber, threshold) || !isFieldOk(row.customer, threshold) || !isFieldOk(row.daysRemaining, threshold);
  return { ...row, needsReview };
}

function blankRow(index: number): ReviewRow {
  return {
    id: `manual-${Date.now()}-${index}`,
    rowIndex: index,
    projectNumber: { rawText: '', value: '', confidence: 100, formatValid: false },
    customer: { rawText: '', value: '', confidence: 100, formatValid: false },
    daysRemaining: { rawText: '', value: '', confidence: 100, formatValid: false },
    verified: false,
    needsReview: true,
  };
}

/** Table for reviewing/editing/verifying/deleting OCR results before the email is generated. Never auto-guesses: low-confidence rows stay flagged until a human edits or verifies them. */
export function ReviewScreen({ rows, confidenceThreshold, onChange, onProceed, onBack }: Props) {
  const [error, setError] = useState<string | null>(null);

  function updateField(rowId: string, field: FieldKey, text: string) {
    const next = rows.map((row) => {
      if (row.id !== rowId) return row;
      const builder = FIELD_BUILDERS[field];
      const updated = { ...row, [field]: builder(text, 100) } as ReviewRow;
      return recomputeNeedsReview(updated, confidenceThreshold);
    });
    onChange(next);
  }

  function toggleVerified(rowId: string) {
    onChange(rows.map((row) => (row.id === rowId ? { ...row, verified: !row.verified } : row)));
  }

  function deleteRow(rowId: string) {
    onChange(rows.filter((row) => row.id !== rowId));
  }

  function addRow() {
    onChange([...rows, blankRow(rows.length)]);
  }

  function handleProceed() {
    const unverified = rows.filter((r) => !r.verified);
    if (unverified.length > 0) {
      setError(`${unverified.length} row(s) still need review before generating the email.`);
      return;
    }
    setError(null);
    onProceed();
  }

  return (
    <div className="review-screen">
      <h2>Review Scanned Projects</h2>
      <p className="review-screen__hint">
        Rows highlighted in red had low-confidence or unrecognized OCR results. Edit the value and check Verified once it's correct.
      </p>

      <div className="review-table-wrap">
        <table className="review-table">
          <thead>
            <tr>
              <th>Project Number</th>
              <th>Customer</th>
              <th>Estimated Days Remaining</th>
              <th>Verified</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id} className={row.needsReview ? 'review-row review-row--flagged' : 'review-row'}>
                <td>
                  <input
                    value={row.projectNumber.value}
                    onChange={(e) => updateField(row.id, 'projectNumber', e.target.value)}
                    aria-label="Project number"
                  />
                </td>
                <td>
                  <input
                    value={row.customer.value}
                    onChange={(e) => updateField(row.id, 'customer', e.target.value)}
                    aria-label="Customer"
                  />
                </td>
                <td>
                  <input
                    value={row.daysRemaining.value}
                    onChange={(e) => updateField(row.id, 'daysRemaining', e.target.value)}
                    aria-label="Estimated days remaining"
                  />
                </td>
                <td className="review-table__verified">
                  <input type="checkbox" checked={row.verified} onChange={() => toggleVerified(row.id)} aria-label="Verified" />
                </td>
                <td>
                  <button className="btn btn--ghost btn--small" onClick={() => deleteRow(row.id)} aria-label="Delete row">
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <button className="btn btn--secondary" onClick={addRow}>
        + Add Row
      </button>

      {error && <p className="review-screen__error">{error}</p>}

      <div className="review-screen__actions">
        <button className="btn btn--ghost" onClick={onBack}>
          Back
        </button>
        <button className="btn btn--primary" onClick={handleProceed} disabled={rows.length === 0}>
          Generate Email
        </button>
      </div>
    </div>
  );
}
