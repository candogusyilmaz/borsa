import { Button, Menu } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconCheck, IconChevronDown, IconPlus } from '@tabler/icons-react';
import { Link, useParams } from '@tanstack/react-router';
import type { ReactNode } from 'react';
import { $api } from '~/api/openapi';
import { ResponsiveOverlay } from '~/shared/components/responsive-overlay/responsive-overlay';
import { useIsMobile } from '~/shared/hooks/use-is-mobile';
import { CreatePortfolioModal } from '../../workflows/create-portfolio-button';
import styles from './portfolio-selector.module.css';

interface PortfolioSelectorProps {
  children: ReactNode;
  className?: string;
  active?: boolean;
}

export function PortfolioSelector({ children, className, active }: PortfolioSelectorProps) {
  const isMobile = useIsMobile();
  const { portfolioId } = useParams({ strict: false });
  const [opened, picker] = useDisclosure();
  const [creating, creation] = useDisclosure();
  const portfolios = $api.useQuery('get', '/api/portfolios', {}, { enabled: opened });
  const positions = $api.useQuery('get', '/api/positions', { params: { query: {} } }, { enabled: opened });
  const choices = portfolios.data?.map((portfolio) => ({
    ...portfolio,
    count: positions.data?.filter((position) => position.portfolio.id === portfolio.id && position.quantity > 0).length
  }));

  function createPortfolio() {
    picker.close();
    creation.open();
  }

  const modal = <CreatePortfolioModal opened={creating} close={creation.close} />;
  const status = <PortfolioStatus pending={portfolios.isPending} error={portfolios.isError} empty={choices?.length === 0} />;

  if (isMobile) {
    return (
      <>
        <button
          type="button"
          className={className}
          data-active={active || undefined}
          aria-current={active ? 'page' : undefined}
          onClick={picker.open}
          aria-haspopup="dialog">
          {children}
        </button>
        <ResponsiveOverlay opened={opened} onClose={picker.close} title="Portfolios">
          {status}
          <ul className={styles.choices}>
            {choices?.map(({ id, name, count }) => (
              <li key={id}>
                <Link to="/portfolios/$portfolioId" params={{ portfolioId: id }} className={styles.choice} onClick={picker.close}>
                  <Choice name={name} count={count} selected={id === portfolioId} />
                </Link>
              </li>
            ))}
          </ul>
          <Button variant="default" fullWidth leftSection={<IconPlus size={16} />} onClick={createPortfolio}>
            Create portfolio
          </Button>
        </ResponsiveOverlay>
        {modal}
      </>
    );
  }

  return (
    <>
      <Menu opened={opened} onChange={picker.set} position="bottom-start" width={260}>
        <Menu.Target>
          <button type="button" className={className} data-active={active || undefined} aria-current={active ? 'page' : undefined}>
            {children} <IconChevronDown size={15} aria-hidden="true" />
          </button>
        </Menu.Target>
        <Menu.Dropdown>
          {status}
          <div className={styles.menuChoices}>
            {choices?.map(({ id, name, count }) => (
              <Menu.Item key={id} renderRoot={(props) => <Link {...props} to="/portfolios/$portfolioId" params={{ portfolioId: id }} />}>
                <Choice name={name} count={count} selected={id === portfolioId} />
              </Menu.Item>
            ))}
          </div>
          <Menu.Divider />
          <Menu.Item leftSection={<IconPlus size={16} />} onClick={createPortfolio}>
            Create portfolio
          </Menu.Item>
        </Menu.Dropdown>
      </Menu>
      {modal}
    </>
  );
}

function PortfolioStatus({ pending, error, empty }: { pending: boolean; error: boolean; empty: boolean }) {
  if (pending) return <output className={styles.status}>Loading portfolios…</output>;
  if (error)
    return (
      <p className={styles.status} role="alert">
        Could not load portfolios.
      </p>
    );
  if (empty) return <p className={styles.status}>No portfolios yet.</p>;
  return null;
}

function Choice({ name, count, selected }: { name: string; count?: number; selected: boolean }) {
  return (
    <span className={styles.choiceContent}>
      <span>
        <strong>{name}</strong>
        {count != null && (
          <small>
            {count} {count === 1 ? 'position' : 'positions'}
          </small>
        )}
      </span>
      {selected && <IconCheck size={17} aria-label="Current portfolio" />}
    </span>
  );
}
