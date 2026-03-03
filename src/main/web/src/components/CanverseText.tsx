import { Text, type TextProps } from '~/lib/shadcn/core';

export function CanverseText(props: TextProps) {
  return (
    <Text inherit c="teal" fw={600} {...props}>
      Canverse
    </Text>
  );
}
