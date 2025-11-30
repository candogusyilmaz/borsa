import { Table } from '@mantine/core';
import { EmptyState } from './empty-state';
import { ErrorState } from './error-state';
import { LoadingState } from './loading-state';

export function TableStateHandler({
  status,
  empty,
  span,
  height = 500
}: {
  status: 'pending' | 'error' | 'success';
  span?: number;
  empty?: boolean;
  height?: number;
}) {
  if (status === 'pending') {
    return (
      <Table.Tr>
        <Table.Td colSpan={span}>
          <LoadingState height={height} message="Loading data..." alt="Please wait while we fetch the data for you." />
        </Table.Td>
      </Table.Tr>
    );
  } else if (status === 'error') {
    return (
      <Table.Tr>
        <Table.Td colSpan={span}>
          <ErrorState
            height={height}
            message="Failed to load data."
            alt="There was an issue retrieving the data. Please try again later."
          />
        </Table.Td>
      </Table.Tr>
    );
  } else if (empty) {
    return (
      <Table.Tr>
        <Table.Td colSpan={span}>
          <EmptyState height={height} message="No data available." alt="There is no data to display at the moment." />
        </Table.Td>
      </Table.Tr>
    );
  }

  return null;
}
