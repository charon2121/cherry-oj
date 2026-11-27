import { Badge, type BadgeVariant } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

// 难度同时使用条形数量（1 / 2 / 3 格）与柔和语义色（绿 / 琥珀 / 红）双重编码：
// 既支持快速色彩扫视，又在色觉差异或高对比度模式下依靠形状准确区分。
const levels: Record<
  string,
  { activeBarClass: string; badgeVariant: BadgeVariant; bars: number; label: string }
> = {
  EASY: {
    bars: 1,
    label: '简单',
    activeBarClass: 'bg-success',
    badgeVariant: 'success',
  },
  MEDIUM: {
    bars: 2,
    label: '中等',
    activeBarClass: 'bg-warning',
    badgeVariant: 'warning',
  },
  HARD: {
    bars: 3,
    label: '困难',
    activeBarClass: 'bg-danger',
    badgeVariant: 'danger',
  },
  UNRATED: {
    bars: 0,
    label: '未评级',
    activeBarClass: 'bg-fg-meta',
    badgeVariant: 'neutral',
  },
};

const fallbackLevel = {
  bars: 0,
  activeBarClass: 'bg-fg-meta',
  badgeVariant: 'neutral' as const,
};

function DifficultyIcon({ difficulty, className }: { difficulty: string; className?: string }) {
  const level = levels[difficulty] ?? { ...fallbackLevel, label: difficulty };
  return (
    <span className={cn('inline-flex items-end gap-0.5', className)} title={level.label}>
      {[0, 1, 2].map((index) => (
        <span
          key={index}
          aria-hidden="true"
          className={cn(
            'w-[3px] rounded-[1px]',
            index === 0 && 'h-[5px]',
            index === 1 && 'h-[8px]',
            index === 2 && 'h-[11px]',
            index < level.bars ? level.activeBarClass : 'bg-border-solid',
          )}
        />
      ))}
      <span className="sr-only">{level.label}</span>
    </span>
  );
}

function DifficultyBadge({ difficulty, className }: { difficulty: string; className?: string }) {
  const level = levels[difficulty] ?? { ...fallbackLevel, label: difficulty };
  return (
    <Badge variant={level.badgeVariant} className={cn('gap-1.5', className)}>
      <DifficultyIcon difficulty={difficulty} />
      <span>{level.label}</span>
    </Badge>
  );
}

function difficultyLabel(value: string) {
  return levels[value]?.label ?? value;
}

export { DifficultyBadge, DifficultyIcon, difficultyLabel };
