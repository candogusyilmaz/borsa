import { Button, Modal, Stack, Text, TextInput } from "@mantine/core";
import { useDisclosure } from "@mantine/hooks";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { mutations } from "~/api";
import { alerts } from "~/lib/alert";

export function ClearMyDataModal() {
  const [opened, { open, close }] = useDisclosure(false);

  return (
    <>
      <Modal
        centered
        opened={opened}
        onClose={close}
        title="Clear My Data"
        transitionProps={{ transition: "fade" }}
      >
        <ClearMyDataForm close={close} />
      </Modal>

      <Button
        variant="subtle"
        color="red.7"
        fw={400}
        fz="xs"
        onClick={open}
        fullWidth
      >
        Clear My Data
      </Button>
    </>
  );
}

const confirmText = "CONFIRM";

function ClearMyDataForm({ close }: { close: () => void }) {
  const client = useQueryClient();
  const [confirmInput, setConfirmInput] = useState("");

  const mutation = useMutation({
    ...mutations.member.clearMyData,
    onSuccess: () => {
      close();
      alerts.success("Successfully cleared all of your data.");
      client.invalidateQueries();
    },
    onError: (res) => {
      console.log(res);
    },
  });

  const isConfirmed = confirmInput === confirmText;

  return (
    <Stack>
      <Text size="sm">
        This action cannot be undone. Please type "{confirmText}" to confirm
        that you want to clear all of your data.
      </Text>
      <TextInput
        placeholder={confirmText}
        value={confirmInput}
        onChange={(e) => setConfirmInput(e.currentTarget.value)}
      />
      <Button
        type="button"
        color="red"
        loading={mutation.isPending || mutation.isSuccess}
        disabled={!isConfirmed}
        onClick={() => mutation.mutate()}
      >
        Clear My Data
      </Button>
    </Stack>
  );
}
