import { useMemo, useState } from 'react';
import type { ReviewRow } from '../../types/domain';
import { generateEmail } from '../../core/email/EmailGenerator';

interface Props {
  rows: ReviewRow[];
  onBack: () => void;
  onStartOver: () => void;
}

export function EmailScreen({ rows, onBack, onStartOver }: Props) {
  const email = useMemo(() => generateEmail(rows), [rows]);
  const [copied, setCopied] = useState<'subject' | 'body' | null>(null);

  async function copy(text: string, which: 'subject' | 'body') {
    await navigator.clipboard.writeText(text);
    setCopied(which);
    setTimeout(() => setCopied((c) => (c === which ? null : c)), 1800);
  }

  return (
    <div className="email-screen">
      <h2>Daily Production Status Email</h2>
      <p className="email-screen__hint">The email is never sent automatically - copy it or open it in your mail client.</p>

      <label className="email-field">
        <span>Subject</span>
        <input readOnly value={email.subject} />
      </label>

      <label className="email-field">
        <span>Body</span>
        <textarea readOnly value={email.body} rows={Math.min(24, email.body.split('\n').length + 2)} />
      </label>

      <div className="email-screen__actions">
        <button className="btn btn--secondary" onClick={() => void copy(email.subject, 'subject')}>
          {copied === 'subject' ? 'Copied!' : 'Copy Subject'}
        </button>
        <button className="btn btn--secondary" onClick={() => void copy(email.body, 'body')}>
          {copied === 'body' ? 'Copied!' : 'Copy Body'}
        </button>
        <a className="btn btn--primary" href={email.mailtoHref}>
          Open Email Client
        </a>
      </div>

      <div className="email-screen__footer">
        <button className="btn btn--ghost" onClick={onBack}>
          Back to Review
        </button>
        <button className="btn btn--ghost" onClick={onStartOver}>
          Start New Scan
        </button>
      </div>
    </div>
  );
}
