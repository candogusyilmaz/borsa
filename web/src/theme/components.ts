import {
  Accordion,
  ActionIcon,
  Alert,
  Anchor,
  AppShell,
  Avatar,
  Badge,
  Button,
  Checkbox,
  Chip,
  CloseButton,
  Code,
  Combobox,
  Divider,
  Drawer,
  Fieldset,
  HoverCard,
  Input,
  Kbd,
  Menu,
  Modal,
  NavLink,
  Notification,
  Pagination,
  Paper,
  Popover,
  Progress,
  Radio,
  SegmentedControl,
  Skeleton,
  Slider,
  Stepper,
  Switch,
  Table,
  Tabs,
  ThemeIcon,
  Timeline,
  Tooltip
} from '@mantine/core';

import buttons from './styles/buttons.module.css';
import controls from './styles/controls.module.css';
import data from './styles/data.module.css';
import feedback from './styles/feedback.module.css';
import inputs from './styles/inputs.module.css';
import navigation from './styles/navigation.module.css';
import overlays from './styles/overlays.module.css';
import selection from './styles/selection.module.css';
import surfaces from './styles/surfaces.module.css';

export const componentOverrides = {
  AppShell: AppShell.extend({
    styles: {
      header: { backgroundColor: 'var(--app-surface)', borderBottomColor: 'var(--app-border-subtle)' },
      navbar: { backgroundColor: 'var(--app-surface)', borderRightColor: 'var(--app-border-subtle)' },
      footer: { backgroundColor: 'var(--app-surface)', borderTopColor: 'var(--app-border-subtle)' },
      aside: { backgroundColor: 'var(--app-surface)', borderLeftColor: 'var(--app-border-subtle)' },
      main: { backgroundColor: 'var(--app-bg)' }
    }
  }),

  Button: Button.extend({
    defaultProps: { size: 'md', radius: 'md' },
    classNames: { root: buttons.button }
  }),

  ActionIcon: ActionIcon.extend({
    defaultProps: { size: 'lg', radius: 'md', variant: 'subtle' },
    classNames: { root: buttons.actionIcon }
  }),

  // Shared defaults cascade to TextInput, PasswordInput, NumberInput, Textarea, Select, MultiSelect
  Input: Input.extend({
    defaultProps: { size: 'md', radius: 'md', variant: 'default' },
    classNames: { input: inputs.input, section: inputs.section }
  }),

  InputWrapper: Input.Wrapper.extend({
    classNames: {
      label: inputs.label,
      description: inputs.description,
      error: inputs.error
    }
  }),

  Combobox: Combobox.extend({
    classNames: {
      dropdown: overlays.dropdown,
      option: inputs.option,
      groupLabel: inputs.groupLabel,
      empty: inputs.empty
    }
  }),

  Checkbox: Checkbox.extend({
    defaultProps: { size: 'md', radius: 'sm' },
    classNames: { input: controls.checkInput, label: controls.controlLabel }
  }),

  Radio: Radio.extend({
    defaultProps: { size: 'md' },
    classNames: { radio: controls.checkInput, label: controls.controlLabel }
  }),

  Switch: Switch.extend({
    defaultProps: { size: 'md' },
    classNames: {
      track: controls.switchTrack,
      thumb: controls.switchThumb,
      label: controls.controlLabel
    }
  }),

  SegmentedControl: SegmentedControl.extend({
    defaultProps: { radius: 'md' },
    classNames: {
      root: selection.segmentedRoot,
      indicator: selection.segmentedIndicator,
      label: selection.segmentedLabel
    }
  }),

  Tabs: Tabs.extend({
    classNames: { list: selection.tabsList, tab: selection.tab }
  }),

  Badge: Badge.extend({
    defaultProps: { radius: 'sm', variant: 'light' },
    classNames: { root: feedback.badge }
  }),

  Paper: Paper.extend({
    defaultProps: { radius: 'md' },
    styles: {
      root: { background: 'var(--app-surface)', borderColor: 'var(--app-border-subtle)' }
    }
  }),

  Modal: Modal.extend({
    defaultProps: {
      centered: true,
      radius: 'lg',
      transitionProps: { duration: 160 }
    },
    classNames: {
      overlay: overlays.overlay,
      content: overlays.modalContent,
      header: overlays.modalHeader,
      title: overlays.modalTitle,
      body: overlays.modalBody
    }
  }),

  Drawer: Drawer.extend({
    defaultProps: { transitionProps: { duration: 180 } },
    classNames: {
      overlay: overlays.overlay,
      content: overlays.drawerContent,
      header: overlays.modalHeader,
      title: overlays.modalTitle,
      body: overlays.modalBody
    }
  }),

  Menu: Menu.extend({
    defaultProps: {
      offset: 6,
      transitionProps: { duration: 120 }
    },
    classNames: {
      dropdown: overlays.dropdown,
      item: overlays.menuItem,
      label: overlays.menuLabel,
      divider: overlays.menuDivider
    }
  }),

  Popover: Popover.extend({
    defaultProps: {
      offset: 6,
      transitionProps: { duration: 120 }
    },
    classNames: { dropdown: overlays.dropdown }
  }),

  Tooltip: Tooltip.extend({
    defaultProps: {
      openDelay: 350,
      transitionProps: { duration: 120 }
    },
    classNames: { tooltip: overlays.tooltip }
  }),

  Table: Table.extend({
    classNames: {
      table: data.table,
      th: data.th,
      td: data.td,
      tr: data.tr
    }
  }),

  Divider: Divider.extend({
    classNames: { root: data.divider }
  }),

  NavLink: NavLink.extend({
    classNames: {
      root: navigation.navLink,
      label: navigation.navLabel,
      description: navigation.navDescription
    }
  }),

  Pagination: Pagination.extend({
    defaultProps: { size: 'md', radius: 'md' },
    classNames: { control: navigation.paginationControl }
  }),

  Skeleton: Skeleton.extend({
    defaultProps: { radius: 'md' }
  }),

  Alert: Alert.extend({
    defaultProps: { radius: 'md' },
    classNames: {
      root: feedback.alert,
      title: feedback.alertTitle,
      message: feedback.alertMessage
    }
  }),

  Notification: Notification.extend({
    defaultProps: { radius: 'md' },
    classNames: {
      root: feedback.notification,
      title: feedback.notificationTitle,
      description: feedback.notificationDescription
    }
  }),

  Accordion: Accordion.extend({
    defaultProps: { radius: 'md' },
    classNames: {
      item: feedback.accordionItem,
      control: feedback.accordionControl,
      label: feedback.accordionLabel,
      chevron: feedback.accordionChevron,
      panel: feedback.accordionPanel
    }
  }),

  Chip: Chip.extend({
    defaultProps: { radius: 'sm' },
    classNames: { label: selection.chipLabel }
  }),

  HoverCard: HoverCard.extend({
    defaultProps: {
      openDelay: 200,
      closeDelay: 150,
      transitionProps: { duration: 120 }
    },
    classNames: { dropdown: overlays.dropdown }
  }),

  Slider: Slider.extend({
    defaultProps: { radius: 'xl' },
    classNames: {
      track: controls.sliderTrack,
      bar: controls.sliderBar,
      thumb: controls.sliderThumb,
      label: controls.sliderLabel,
      mark: controls.sliderMark,
      markLabel: controls.sliderMarkLabel
    }
  }),

  Progress: Progress.extend({
    defaultProps: { radius: 'xl' },
    classNames: { root: data.progressRoot }
  }),

  Timeline: Timeline.extend({
    classNames: {
      itemBullet: data.timelineItemBullet,
      itemTitle: data.timelineItemTitle,
      itemBody: data.timelineItemBody
    }
  }),

  Stepper: Stepper.extend({
    classNames: {
      stepIcon: navigation.stepperStepIcon,
      step: navigation.stepperStep,
      separator: navigation.stepperSeparator,
      stepLabel: navigation.stepperStepLabel,
      stepDescription: navigation.stepperStepDescription
    }
  }),

  Anchor: Anchor.extend({
    defaultProps: { underline: 'hover' },
    styles: { root: { color: 'var(--app-accent)' } }
  }),

  Code: Code.extend({
    classNames: { root: surfaces.code }
  }),

  Kbd: Kbd.extend({
    classNames: { root: surfaces.kbd }
  }),

  Fieldset: Fieldset.extend({
    classNames: { root: surfaces.fieldsetRoot, legend: surfaces.fieldsetLegend }
  }),

  CloseButton: CloseButton.extend({
    defaultProps: { size: 'md', radius: 'md' },
    classNames: { root: buttons.actionIcon }
  }),

  Avatar: Avatar.extend({
    defaultProps: { radius: 'md' },
    classNames: { placeholder: surfaces.avatarPlaceholder }
  }),

  ThemeIcon: ThemeIcon.extend({
    defaultProps: { radius: 'md', variant: 'light' }
  })
};
