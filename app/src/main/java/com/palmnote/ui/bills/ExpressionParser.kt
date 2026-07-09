package com.palmnote.ui.bills

object ExpressionParser {
    
    fun evaluate(expression: String): Double {
        if (expression.isBlank()) return 0.0
        
        val trimmed = expression.trim()
        
        // 移除尾部运算符
        val cleaned = trimmed.trimEnd('+', '-', '*', '/')
        
        if (cleaned.isEmpty()) return 0.0
        
        return try {
            parseExpression(cleaned)
        } catch (e: Exception) {
            0.0
        }
    }
    
    private fun parseExpression(expression: String): Double {
        val tokens = tokenize(expression)
        if (tokens.isEmpty()) return 0.0
        
        var result = tokens[0] as Double
        var i = 1
        
        while (i < tokens.size - 1) {
            val operator = tokens[i] as Char
            val operand = tokens[i + 1] as Double
            
            result = when (operator) {
                '+' -> result + operand
                '-' -> result - operand
                '*' -> result * operand
                '/' -> if (operand != 0.0) result / operand else 0.0
                else -> result
            }
            i += 2
        }
        
        return result
    }
    
    private fun tokenize(expression: String): List<Any> {
        val tokens = mutableListOf<Any>()
        var currentNumber = StringBuilder()
        var i = 0
        
        while (i < expression.length) {
            val c = expression[i]
            
            when {
                c.isDigit() || c == '.' -> {
                    currentNumber.append(c)
                }
                c == '+' || c == '-' || c == '*' || c == '/' -> {
                    if (currentNumber.isNotEmpty()) {
                        tokens.add(currentNumber.toString().toDoubleOrNull() ?: 0.0)
                        currentNumber = StringBuilder()
                    }
                    tokens.add(c)
                }
                c == ' ' -> {
                    // 忽略空格
                }
            }
            i++
        }
        
        if (currentNumber.isNotEmpty()) {
            tokens.add(currentNumber.toString().toDoubleOrNull() ?: 0.0)
        }
        
        return tokens
    }
    
    fun isValid(amount: String, category: String): Boolean {
        if (amount.isBlank() || category.isBlank()) return false
        
        val evaluated = evaluate(amount)
        if (evaluated <= 0) return false
        
        return true
    }
}
