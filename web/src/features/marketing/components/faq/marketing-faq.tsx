import { Accordion, Badge, Container } from '@mantine/core';
import { siteConfig } from '@/shared/config/site';
import classes from './marketing-faq.module.css';

const FAQS = [
  {
    id: 'faq-1',
    question: `How does ${siteConfig.name} guarantee ledger accuracy?`,
    answer: `Unlike simple portfolio trackers that merely record balance snapshots, ${siteConfig.name} employs an immutable double-entry bookkeeping engine. Every cash deposit, dividend receipt, fee, and trade settlement is recorded as paired debit and credit entries enforced by PostgreSQL ACID constraints. Transactions cannot drift out of balance.`
  },
  {
    id: 'faq-2',
    question: 'How is session security handled without persistent access tokens?',
    answer: `${siteConfig.name} utilizes a high-security dual-token lifecycle. Short-lived access tokens are held exclusively in volatile browser memory and session storage (cleared when tabs close). Long-lived refresh tokens are delivered solely via HttpOnly, SameSite=Lax, Secure cookies that are inaccessible to JavaScript, insulating sessions against XSS token harvesting.`
  },
  {
    id: 'faq-3',
    question: 'What happens when a statement discrepancy is found during reconciliation?',
    answer:
      'The automated reconciliation engine compares external brokerage statement line items against your internal cash and position journals. Any discrepancy is flagged in a batch reconciliation report, and the system prepares explicit, audit-trailed adjustment proposals rather than silently overriding historical records.'
  },
  {
    id: 'faq-4',
    question: 'How does the architecture achieve sub-millisecond responsiveness?',
    answer:
      'The web client is built on React 19, Vite 8, and TanStack Router with aggressive code splitting. Server state is managed by TanStack Query, providing instant UI transitions, optimistic updates, and background refetching. No heavy third-party CSS or redundant runtime frameworks are loaded.'
  }
];

export function MarketingFaq() {
  return (
    <section id="faq" className={classes.faqSection} aria-labelledby="faq-heading">
      <Container size="lg">
        <div className={classes.sectionHeader}>
          <Badge variant="light" color="indigo" size="md" className={classes.badge}>
            Common Questions
          </Badge>
          <h2 id="faq-heading" className={classes.title}>
            Frequently Asked Questions
          </h2>
          <p className={classes.subtitle}>
            Everything you need to know about the accounting engine, session lifecycle, and system capabilities.
          </p>
        </div>

        <div className={classes.accordionWrapper}>
          <Accordion variant="separated" radius="md" defaultValue="faq-1">
            {FAQS.map((faq) => (
              <Accordion.Item key={faq.id} value={faq.id} className={classes.item}>
                <Accordion.Control className={classes.control}>{faq.question}</Accordion.Control>
                <Accordion.Panel className={classes.panel}>{faq.answer}</Accordion.Panel>
              </Accordion.Item>
            ))}
          </Accordion>
        </div>
      </Container>
    </section>
  );
}
