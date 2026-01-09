package com.portablestorage.util;

import com.github.stuxuhai.jpinyin.PinyinHelper;
import com.github.stuxuhai.jpinyin.PinyinFormat;
import java.util.*;

/**
 * 拼音工具类
 * 提供中文拼音搜索匹配功能，支持首字母、全拼和混合匹配
 */
public class PinyinUtils {
    /**
     * 检查目标字符串是否匹配查询字符串（支持拼音搜索）
     * @param target 目标文本（如：苹果）
     * @param query 查询文本（如：pg, pingg, pingguo）
     * @return 是否匹配
     */
    public static boolean matches(String target, String query) {
        if (query == null || query.isEmpty()) return true;
        if (target == null || target.isEmpty()) return false;

        String lowerTarget = target.toLowerCase();
        String lowerQuery = query.toLowerCase().trim();

        // 原文包含匹配
        if (lowerTarget.contains(lowerQuery)) return true;

        // 拼音匹配
        String pinyinQuery = lowerQuery.replace(" ", "");
        if (pinyinQuery.isEmpty()) return true;
        
        return pinyinMatch(lowerTarget, pinyinQuery);
    }

    private static boolean pinyinMatch(String target, String query) {
        List<String[]> targetPinyins = new ArrayList<>();
        for (char c : target.toCharArray()) {
            String[] pinyins = PinyinHelper.convertToPinyinArray(c, PinyinFormat.WITHOUT_TONE);
            if (pinyins != null && pinyins.length > 0) {
                // 去重并转小写
                Set<String> set = new HashSet<>();
                for (String p : pinyins) {
                    set.add(p.toLowerCase());
                }
                targetPinyins.add(set.toArray(new String[0]));
            } else {
                targetPinyins.add(new String[]{String.valueOf(c).toLowerCase()});
            }
        }

        // 尝试从每一个位置开始匹配
        for (int i = 0; i < targetPinyins.size(); i++) {
            if (matchRecursive(targetPinyins, i, query, 0)) return true;
        }
        return false;
    }

    /**
     * 核心匹配逻辑：支持首字母、全拼、及其混合的残缺匹配
     */
    private static boolean matchRecursive(List<String[]> targetPinyins, int charIdx, String query, int queryIdx) {
        if (queryIdx >= query.length()) return true; 
        if (charIdx >= targetPinyins.size()) return false; 

        String[] pinyins = targetPinyins.get(charIdx);
        
        for (String p : pinyins) {
            if (p.isEmpty()) continue;
            
            // 匹配首字母
            if (query.charAt(queryIdx) == p.charAt(0)) {
                // 尝试作为首字母匹配：跳到下一个字符
                if (matchRecursive(targetPinyins, charIdx + 1, query, queryIdx + 1)) return true;
                
                // 匹配全拼或部分全拼
                int j = 1; 
                while (j < p.length() && queryIdx + j < query.length() && p.charAt(j) == query.charAt(queryIdx + j)) {
                    j++;
                    // 每次增加一个拼音字母匹配，都尝试从下一个字符继续匹配剩余查询
                    if (matchRecursive(targetPinyins, charIdx + 1, query, queryIdx + j)) return true;
                }
            } else if (p.length() == 1 && p.charAt(0) == query.charAt(queryIdx)) {
                // 非中文字符匹配
                if (matchRecursive(targetPinyins, charIdx + 1, query, queryIdx + 1)) return true;
            }
        }

        return false;
    }
}
