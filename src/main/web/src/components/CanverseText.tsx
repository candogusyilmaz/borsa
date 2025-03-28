import {Text, type TextProps} from '@mantine/core';

export function CanverseText(props: TextProps) {
    return (
        <Text inherit c="teal" fw={600} {...props}>
            Canverse
        </Text>
    );
}
