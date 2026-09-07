import { useEffect, useState, useSyncExternalStore } from 'react';

import { CodeDraftController, type CodeDraftIdentity } from '../lib/code-draft';

/**
 * Mount only for a confirmed authenticated identity. The parent keys this editor by
 * account/problem/version/language, so unmount flushes the old controller's own key.
 * starterCode changes for the same version never replace an existing edit buffer.
 */
export function useCodeDraft(options: CodeDraftIdentity & { starterCode: string }) {
  const [controller] = useState(() => new CodeDraftController(options, options.starterCode));
  const snapshot = useSyncExternalStore(controller.subscribe, controller.getSnapshot);

  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === null || event.key.startsWith(controller.key)) controller.refresh();
    };
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      controller.flush();
      if (controller.getSnapshot().hasUnsavedChanges) {
        event.preventDefault();
        event.returnValue = '';
      }
    };
    window.addEventListener('storage', onStorage);
    window.addEventListener('pagehide', controller.flush);
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => {
      window.removeEventListener('storage', onStorage);
      window.removeEventListener('pagehide', controller.flush);
      window.removeEventListener('beforeunload', onBeforeUnload);
      controller.detach();
    };
  }, [controller]);

  return {
    ...snapshot,
    setValue: controller.setValue,
    flush: controller.flush,
    retrySave: controller.retrySave,
    resetToStarter: controller.resetToStarter,
    resolveConflict: controller.resolveConflict,
  };
}
