import { Center, Loader } from '~/lib/shadcn/core';

export function LoadingView() {
  return (
    <Center h="100%" mih={100}>
      <Loader />
    </Center>
  );
}
