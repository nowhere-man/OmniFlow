import { Icon } from "@iconify/react";
import { FC } from "react";

export interface CategoryIconProps {
  name?: string | null;
  size?: number;
  className?: string;
}

export const CategoryIcon: FC<CategoryIconProps> = ({ name, size = 18, className = "" }) => {
  if (!name) return <Icon icon="fluent-emoji-flat:question-mark" width={size} height={size} className={className} />;
  
  // Backward compatibility with previous lucide icons, map them to fluent-emoji if they don't contain a colon
  let iconName = name;
  if (!iconName.includes(":")) {
    iconName = "fluent-emoji-flat:" + iconName.toLowerCase().replace(/([a-z])([A-Z])/g, '$1-$2'); // camelCase to kebab-case
  }
  
  return <Icon icon={iconName} width={size} height={size} className={className} />;
};
