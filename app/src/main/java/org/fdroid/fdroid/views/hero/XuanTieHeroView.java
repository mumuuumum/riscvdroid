package org.fdroid.fdroid.views.hero;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;
import org.fdroid.fdroid.R;

/**
 * XuanTie Hero Section 自定义头部视图
 * 实现紫色渐变背景和科技感几何线条图案
 */
public class XuanTieHeroView extends View {

    private Paint backgroundPaint;
    private Paint patternPaint;
    private LinearGradient gradientShader;
    private int width;
    private int height;

    public XuanTieHeroView(Context context) {
        super(context);
        init();
    }

    public XuanTieHeroView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public XuanTieHeroView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        patternPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        patternPaint.setColor(ContextCompat.getColor(getContext(), R.color.xuantie_hero_pattern_line));
        patternPaint.setStrokeWidth(1.0f);
        patternPaint.setAlpha(30); // 低透明度，营造subtle效果
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;

        // 创建水平渐变
        gradientShader = new LinearGradient(
                0, 0, width, 0,
                ContextCompat.getColor(getContext(), R.color.xuantie_hero_gradient_start),
                ContextCompat.getColor(getContext(), R.color.xuantie_hero_gradient_end),
                Shader.TileMode.CLAMP
        );
        backgroundPaint.setShader(gradientShader);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制渐变背景
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        // 绘制科技感几何线条图案
        drawTechPattern(canvas);
    }

    /**
     * 绘制科技感几何线条图案
     * 模拟芯片电路板的线条效果
     */
    private void drawTechPattern(Canvas canvas) {
        final int lineHeight = 2; // 线条间隔
        final int shortLineLength = 40; // 短线条长度
        final int longLineLength = 80; // 长线条长度

        // 绘制水平线条网格
        for (int y = 20; y < height - 20; y += lineHeight * 8) {
            // 长线条
            canvas.drawLine(20, y, 20 + longLineLength, y, patternPaint);

            // 中间断点线条
            canvas.drawLine(20 + longLineLength + 20, y, width - 20, y, patternPaint);
        }

        // 绘制垂直连接线
        for (int x = 60; x < width - 60; x += lineHeight * 12) {
            // 垂直短线条
            canvas.drawLine(x, 20, x, 20 + shortLineLength, patternPaint);

            // 下方的连接线条
            if (x + lineHeight * 6 < width - 60) {
                canvas.drawLine(x + lineHeight * 6, height - 20 - shortLineLength,
                               x + lineHeight * 6, height - 20, patternPaint);
            }
        }

        // 绘制对角线装饰（增加科技感）
        for (int i = 0; i < 3; i++) {
            int startX = 100 + i * 150;
            int startY = 30 + i * 20;

            // 短对角线
            canvas.drawLine(startX, startY, startX + 20, startY + 10, patternPaint);
            canvas.drawLine(startX + 25, startY + 10, startX + 45, startY, patternPaint);
        }
    }
}