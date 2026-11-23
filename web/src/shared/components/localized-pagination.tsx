import { useLingui } from '@lingui/react/macro';
import { Pagination, type PaginationProps } from '@mantine/core';

export function LocalizedPagination(props: PaginationProps & { 'aria-label': string }) {
  const { t } = useLingui();
  const labels = {
    first: t`First page`,
    previous: t`Previous page`,
    next: t`Next page`,
    last: t`Last page`
  };

  return <Pagination getControlProps={(control) => ({ 'aria-label': labels[control] })} {...props} />;
}
