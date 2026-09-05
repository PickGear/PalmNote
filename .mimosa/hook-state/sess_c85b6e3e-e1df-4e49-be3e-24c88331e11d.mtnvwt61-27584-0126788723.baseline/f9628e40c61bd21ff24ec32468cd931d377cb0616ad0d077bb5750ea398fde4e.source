"""Generate widget preview PNG images for PalmNote widgets - one per widget type."""
import os
from PIL import Image, ImageDraw, ImageFont

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "app", "src", "main", "res", "drawable-nodpi")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Preview size (3x density, medium-ish)
W, H = 720, 480

# Colors
GLASS_BG = (255, 255, 255, 230)
BORDER_COLOR = (48, 0, 0, 0)
TEXT_DARK = (26, 26, 26)
TEXT_MED = (102, 102, 102)
TEXT_LIGHT = (170, 170, 170)

WIDGETS = {
    "bill": {
        "accent1": (220, 80, 60), "accent2": (240, 120, 80),
        "label": "账单概览", "data": "¥1,280", "sub": "本月支出", "bottom": "8月31日 · 已记录15笔",
    },
    "todo": {
        "accent1": (76, 175, 80), "accent2": (129, 199, 132),
        "label": "待办事项", "data": "3/5", "sub": "已完成", "bottom": "还剩2项未完成",
    },
    "counter": {
        "accent1": (255, 152, 0), "accent2": (255, 183, 77),
        "label": "生命倒计时", "data": "28,473", "sub": "已度过天数", "bottom": "进度 78%",
    },
    "asset": {
        "accent1": (63, 81, 181), "accent2": (121, 134, 203),
        "label": "资产总览", "data": "¥128.5K", "sub": "总资产", "bottom": "3项持有中",
    },
    "vault": {
        "accent1": (156, 39, 176), "accent2": (186, 104, 200),
        "label": "文件保险库", "data": "12", "sub": "份文件", "bottom": "最近30天保存5份",
    },
    "quick_bill": {
        "accent1": (0, 150, 136), "accent2": (77, 182, 172),
        "label": "快捷记账", "data": "午餐 ¥25.00", "sub": "餐饮 · 今天", "bottom": "今日已记3笔",
    },
    "dashboard": {
        "accent1": (33, 150, 243), "accent2": (100, 181, 246),
        "label": "数据看板", "data": "本月 ¥1,280", "sub": "支出概况", "bottom": "资产 ¥128.5K",
    },
}


def draw_rounded_rect(draw, xy, radius, fill=None, outline=None, width=1):
    x1, y1, x2, y2 = xy
    r = radius
    draw.pieslice([x1, y1, x1+2*r, y1+2*r], 180, 270, fill=fill)
    draw.pieslice([x2-2*r, y1, x2, y1+2*r], 270, 360, fill=fill)
    draw.pieslice([x1, y2-2*r, x1+2*r, y2], 90, 180, fill=fill)
    draw.pieslice([x2-2*r, y2-2*r, x2, y2], 0, 90, fill=fill)
    draw.rectangle([x1+r, y1, x2-r, y1+r], fill=fill)
    draw.rectangle([x1+r, y2-r, x2-r, y2], fill=fill)
    draw.rectangle([x1, y1+r, x2, y2-r], fill=fill)
    if outline:
        draw.line([x1+r, y1, x2-r, y1], fill=outline, width=width)
        draw.line([x1+r, y2, x2-r, y2], fill=outline, width=width)
        draw.line([x1, y1+r, x1, y2-r], fill=outline, width=width)
        draw.line([x2, y1+r, x2, y2-r], fill=outline, width=width)


def draw_accent_bar(draw, x, y, h, c1, c2):
    for i in range(h):
        ratio = i / max(h, 1)
        r = int(c1[0]*(1-ratio) + c2[0]*ratio)
        g = int(c1[1]*(1-ratio) + c2[1]*ratio)
        b = int(c1[2]*(1-ratio) + c2[2]*ratio)
        draw.line([x, y+i, x+4, y+i], fill=(r, g, b, 255))


def generate(name, info):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Card
    m = 8
    draw_rounded_rect(draw, (m, m, W-m, H-m), 28, fill=GLASS_BG, outline=BORDER_COLOR, width=2)

    # Accent bar
    draw_accent_bar(draw, m+20, m+30, H-m*2-60, info["accent1"], info["accent2"])

    # Live dot
    draw.ellipse([m+42, m+42, m+58, m+58], fill=info["accent1"])

    # Fonts
    try:
        fnt_label = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 28)
        fnt_data = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 52)
        fnt_sub = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 22)
        fnt_bottom = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 18)
    except OSError:
        fnt_label = fnt_data = fnt_sub = fnt_bottom = ImageFont.load_default()

    tx = m + 72

    # Label
    draw.text((tx, m+30), info["label"], fill=TEXT_MED, font=fnt_label)
    # Data
    draw.text((tx, m+70), info["data"], fill=TEXT_DARK, font=fnt_data)
    # Sub
    draw.text((tx, m+132), info["sub"], fill=TEXT_LIGHT, font=fnt_sub)
    # Bottom
    draw.text((tx, H-m-44), info["bottom"], fill=TEXT_LIGHT, font=fnt_bottom)

    path = os.path.join(OUTPUT_DIR, f"widget_preview_{name}.png")
    img.save(path, "PNG")
    print(f"  {name}: {os.path.getsize(path)} bytes")


if __name__ == "__main__":
    for name, info in WIDGETS.items():
        generate(name, info)
    print(f"\nDone! {len(WIDGETS)} preview images.")
