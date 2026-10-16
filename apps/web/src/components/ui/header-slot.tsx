import { createContext, type ReactNode, useContext, useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';

// 应用外壳顶栏只有一条（linear.app 的详情页也只有一条：面包屑在左，页面操作在右）。
// 页面通过这里把"当前对象名"追加到面包屑末尾、把页面级操作放到顶栏右侧，
// 而不是在主内容里再叠一条自己的顶栏。
type HeaderSlotValue = Readonly<{
  actionsTarget: HTMLElement | null;
  pageCrumb: string | null;
  setActionsTarget: (element: HTMLElement | null) => void;
  setPageCrumb: (label: string | null) => void;
}>;

const HeaderSlotContext = createContext<HeaderSlotValue | null>(null);

function HeaderSlotProvider({ children }: { children: ReactNode }) {
  const [actionsTarget, setActionsTarget] = useState<HTMLElement | null>(null);
  const [pageCrumb, setPageCrumb] = useState<string | null>(null);
  const value = useMemo(
    () => ({ actionsTarget, pageCrumb, setActionsTarget, setPageCrumb }),
    [actionsTarget, pageCrumb],
  );
  return <HeaderSlotContext value={value}>{children}</HeaderSlotContext>;
}

function useHeaderSlot() {
  return useContext(HeaderSlotContext);
}

/** 把当前对象名（例如题目标题）追加为面包屑末项；离开页面时自动撤回。 */
function usePageCrumb(label: string) {
  const slot = useHeaderSlot();
  const setPageCrumb = slot?.setPageCrumb;
  useEffect(() => {
    if (setPageCrumb === undefined) return undefined;
    setPageCrumb(label);
    return () => setPageCrumb(null);
  }, [label, setPageCrumb]);
}

/** 渲染到外壳顶栏右侧。外壳之外（测试、Storybook）原地渲染。 */
function HeaderActions({ children }: { children: ReactNode }) {
  const slot = useHeaderSlot();
  if (slot === null) return <>{children}</>;
  if (slot.actionsTarget === null) return null;
  return createPortal(children, slot.actionsTarget);
}

/** 外壳顶栏右侧的挂载点。 */
function HeaderActionsTarget({ className }: { className?: string }) {
  const slot = useHeaderSlot();
  return <div ref={slot?.setActionsTarget} className={className} />;
}

export { HeaderActions, HeaderActionsTarget, HeaderSlotProvider, useHeaderSlot, usePageCrumb };
