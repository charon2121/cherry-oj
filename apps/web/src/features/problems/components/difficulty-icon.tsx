import { cn } from '@/lib/utils';

// 难度是有序量，用**条形数量**编码，不用彩色文字。
//
// 此前每行都有一个饱和色的「简单/中等/困难」，六行就是六处饱和色，密集列表因此显得吵。
// 参照对同为有序量的优先级用的是三根条的图标：形状本身携带顺序，颜色可以完全不用。
// 这样每屏的饱和色从「行数 × 1」降到 0，色彩预算留给真正需要强调的地方。
//
// 形状承载信息，因此不依赖颜色即可分辨；文本通过 sr-only 与 title 保留，
// 窄屏折行区也会显示文字。
const levels: Record<string, { bars: number; label: string }> = {
  EASY: { bars: 1, label: '简单' },
  MEDIUM: { bars: 2, label: '中等' },
  HARD: { bars: 3, label: '困难' },
  UNRATED: { bars: 0, label: '未评级' },
};

function DifficultyIcon({ difficulty, className }: { difficulty: string; className?: string }) {
  const level = levels[difficulty] ?? { bars: 0, label: difficulty };
  return (
    <span className={cn('inline-flex items-end gap-px', className)} title={level.label}>
      {[0, 1, 2].map((index) => (
        <span
          key={index}
          aria-hidden="true"
          className={cn(
            'w-[3px] rounded-[1px]',
            index === 0 && 'h-[5px]',
            index === 1 && 'h-[8px]',
            index === 2 && 'h-[11px]',
            index < level.bars ? 'bg-fg-muted' : 'bg-border-solid',
          )}
        />
      ))}
      <span className="sr-only">{level.label}</span>
    </span>
  );
}

function difficultyLabel(value: string) {
  return levels[value]?.label ?? value;
}

export { DifficultyIcon, difficultyLabel };
