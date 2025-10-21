package com.portable.storage.client.util;

import java.util.List;
import java.util.Locale;

import com.github.promeg.pinyinhelper.Pinyin;
import com.portable.storage.client.ClientConfig;

/**
 * 拼音搜索辅助工具类
 * 提供中文转拼音和拼音搜索匹配功能
 */
public class PinyinSearchHelper {
    
    /**
     * 检查文本是否匹配搜索查询（支持拼音搜索）
     * 
     * @param text 要搜索的文本
     * @param query 搜索查询
     * @return 是否匹配
     */
    public static boolean matches(String text, String query) {
        if (text == null || query == null) {
            return false;
        }
        
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        
        // 1. 直接文本匹配
        if (lowerText.contains(lowerQuery)) {
            return true;
        }
        
        // 2. 拼音匹配（如果启用）
        if (ClientConfig.getInstance().pinyinSearch) {
            return matchesPinyin(text, lowerQuery);
        }
        
        return false;
    }
    
    /**
     * 检查文本是否通过拼音匹配搜索查询
     * 支持多音字的不同读音
     * 
     * @param text 要搜索的文本
     * @param lowerQuery 小写的搜索查询
     * @return 是否匹配
     */
    private static boolean matchesPinyin(String text, String lowerQuery) {
        try {
            // 1. 首先尝试默认拼音匹配
            String defaultPinyin = Pinyin.toPinyin(text, " "); // 使用空格分隔符
            String lowerDefaultPinyin = defaultPinyin.toLowerCase(Locale.ROOT);
            
            // 检查默认拼音是否包含查询
            if (lowerDefaultPinyin.contains(lowerQuery)) {
                return true;
            }
            
            // 检查默认拼音首字母匹配
            if (extractInitials(lowerDefaultPinyin).contains(lowerQuery)) {
                return true;
            }
            
            // 2. 如果包含多音字，尝试所有可能的拼音组合
            if (containsPolyphone(text)) {
                List<String> possiblePinyins = PolyphoneDictionary.generateAllPossiblePinyins(text);
                
                for (String pinyin : possiblePinyins) {
                    String lowerPinyin = pinyin.toLowerCase(Locale.ROOT);
                    
                    // 检查完整拼音匹配
                    if (lowerPinyin.contains(lowerQuery)) {
                        return true;
                    }
                    
                    // 检查拼音首字母匹配
                    if (extractInitials(lowerPinyin).contains(lowerQuery)) {
                        return true;
                    }
                }
            }
            
            return false;
            
        } catch (Exception e) {
            // 如果拼音转换失败，返回false
            return false;
        }
    }
    
    /**
     * 检查文本是否包含多音字
     * 
     * @param text 要检查的文本
     * @return 是否包含多音字
     */
    private static boolean containsPolyphone(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        for (int i = 0; i < text.length(); i++) {
            String charStr = String.valueOf(text.charAt(i));
            if (PolyphoneDictionary.isPolyphone(charStr)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 提取拼音的首字母
     * 正确提取每个拼音音节的第一个字母
     * 
     * @param pinyin 拼音字符串
     * @return 首字母字符串
     */
    private static String extractInitials(String pinyin) {
        if (pinyin == null || pinyin.isEmpty()) {
            return "";
        }
        
        StringBuilder initials = new StringBuilder();
        String[] syllables = pinyin.split("\\s+"); // 按空格分割拼音音节
        
        for (String syllable : syllables) {
            if (!syllable.isEmpty()) {
                // 找到第一个字母
                for (int i = 0; i < syllable.length(); i++) {
                    char c = syllable.charAt(i);
                    if (Character.isLetter(c)) {
                        initials.append(Character.toLowerCase(c));
                        break; // 只取第一个字母
                    }
                }
            }
        }
        
        return initials.toString();
    }
    
    /**
     * 获取文本的拼音表示（用于调试）
     * 如果包含多音字，返回第一个可能的拼音组合
     * 
     * @param text 要转换的文本
     * @return 拼音字符串
     */
    public static String getPinyin(String text) {
        if (text == null) {
            return "";
        }
        
        try {
            // 如果包含多音字，返回第一个可能的拼音组合
            if (containsPolyphone(text)) {
                List<String> possiblePinyins = PolyphoneDictionary.generateAllPossiblePinyins(text);
                if (!possiblePinyins.isEmpty()) {
                    return possiblePinyins.get(0);
                }
            }
            
            // 回退到默认拼音转换
            return Pinyin.toPinyin(text, " "); // 使用空格分隔符
        } catch (Exception e) {
            return text;
        }
    }
    
    /**
     * 获取文本的拼音首字母（用于调试）
     * 如果包含多音字，返回第一个可能的拼音组合的首字母
     * 
     * @param text 要转换的文本
     * @return 拼音首字母字符串
     */
    public static String getPinyinInitials(String text) {
        if (text == null) {
            return "";
        }
        
        try {
            String pinyin = getPinyin(text).toLowerCase(Locale.ROOT);
            return extractInitials(pinyin);
        } catch (Exception e) {
            return text;
        }
    }
    
    /**
     * 获取文本的所有可能拼音组合（用于调试）
     * 
     * @param text 要转换的文本
     * @return 所有可能的拼音列表
     */
    public static List<String> getAllPossiblePinyins(String text) {
        if (text == null) {
            return List.of();
        }
        
        try {
            if (containsPolyphone(text)) {
                return PolyphoneDictionary.generateAllPossiblePinyins(text);
            } else {
                // 没有多音字，返回默认拼音
                String defaultPinyin = Pinyin.toPinyin(text, " "); // 使用空格分隔符
                return List.of(defaultPinyin);
            }
        } catch (Exception e) {
            return List.of(text);
        }
    }
}
