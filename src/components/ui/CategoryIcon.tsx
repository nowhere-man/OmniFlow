import * as Icons from "lucide-react";
import { FC } from "react";

export interface CategoryIconProps {
  name?: string | null;
  size?: number;
  className?: string;
}

export const CategoryIcon: FC<CategoryIconProps> = ({ name, size = 18, className = "" }) => {
  if (!name) return <Icons.CircleDashed size={size} className={className} />;
  
  // @ts-expect-error dynamic access
  const IconComponent = Icons[name] as FC<{ size?: number; className?: string }> | undefined;
  
  if (!IconComponent) return <Icons.CircleDashed size={size} className={className} />;
  
  return <IconComponent size={size} className={className} />;
};
