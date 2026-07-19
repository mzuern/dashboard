import type { ReviewRow } from '../../types/domain';

export interface GeneratedEmail {
  subject: string;
  body: string;
  mailtoHref: string;
}

function formatDate(date: Date): string {
  return date.toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });
}

export function generateEmail(rows: ReviewRow[], date: Date = new Date()): GeneratedEmail {
  const subject = `Daily Production Status - ${formatDate(date)}`;

  const projectBlocks = rows.map((row) =>
    [`Project ${row.projectNumber.value}`, `Customer: ${row.customer.value}`, `Estimated Days Remaining: ${row.daysRemaining.value}`].join(
      '\n',
    ),
  );

  const body = ['Daily Production Status', '', 'Project Updates', '', projectBlocks.join('\n\n'), '', 'End of Report'].join('\n');

  const mailtoHref = `mailto:?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;

  return { subject, body, mailtoHref };
}
