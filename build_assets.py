import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_card_with_badge(target_w, target_h):
    """Generates the card + watermark + badge on a transparent canvas."""
    size = 1024
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    card_w = int(size * 0.74)
    card_h = int(size * 0.48)
    card_x = int((size - card_w) / 2)
    card_y = int(size * 0.22)
    card_r = int(size * 0.04)

    # 1. Shadow for ID Card
    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sh_draw = ImageDraw.Draw(shadow)
    sh_draw.rounded_rectangle(
        [card_x, card_y + 16, card_x + card_w, card_y + card_h + 24],
        radius=card_r,
        fill=(5, 10, 25, 140)
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    canvas.alpha_composite(shadow)

    # 2. ID Card Base
    card = Image.new("RGBA", (card_w, card_h), (0, 0, 0, 0))
    card_draw = ImageDraw.Draw(card)
    
    # Card surface gradient
    for cy in range(card_h):
        c_ratio = cy / float(card_h)
        c_val = int(255 * (1 - c_ratio) + 241 * c_ratio)
        card_draw.line([(0, cy), (card_w, cy)], fill=(c_val, c_val + 2, c_val + 5, 255))
        
    card_mask = Image.new("L", (card_w, card_h), 0)
    card_mask_draw = ImageDraw.Draw(card_mask)
    card_mask_draw.rounded_rectangle([0, 0, card_w, card_h], radius=card_r, fill=255)

    # Top ribbon
    ribbon_h = int(card_h * 0.12)
    ribbon = Image.new("RGBA", (card_w, ribbon_h), (0, 0, 0, 0))
    ribbon_draw = ImageDraw.Draw(ribbon)
    for rx in range(card_w):
        r_ratio = rx / float(card_w)
        rr = int(6 * (1 - r_ratio) + 99 * r_ratio)
        rg = int(182 * (1 - r_ratio) + 102 * r_ratio)
        rb = int(212 * (1 - r_ratio) + 241 * r_ratio)
        ribbon_draw.line([(rx, 0), (rx, ribbon_h)], fill=(rr, rg, rb, 255))
    card.paste(ribbon, (0, 0))

    card_draw.rounded_rectangle([0, 0, card_w - 1, card_h - 1], radius=card_r, outline=(226, 232, 240, 255), width=2)

    # Photo Box
    photo_w = int(card_w * 0.28)
    photo_h = int(card_h * 0.60)
    photo_x = int(card_w * 0.08)
    photo_y = int(card_h * 0.22)
    
    card_draw.rounded_rectangle(
        [photo_x, photo_y, photo_x + photo_w, photo_y + photo_h],
        radius=14,
        fill=(226, 232, 240, 255),
        outline=(203, 213, 225, 255),
        width=2
    )
    
    av_cx = photo_x + photo_w // 2
    av_cy = photo_y + int(photo_h * 0.40)
    head_rad = int(photo_w * 0.22)
    card_draw.ellipse(
        [av_cx - head_rad, av_cy - head_rad, av_cx + head_rad, av_cy + head_rad],
        fill=(148, 163, 184, 255)
    )
    card_draw.chord(
        [av_cx - int(photo_w * 0.38), av_cy + int(head_rad * 0.8),
         av_cx + int(photo_w * 0.38), photo_y + photo_h + 10],
        start=180, end=0,
        fill=(148, 163, 184, 255)
    )

    # Chip
    chip_x = int(card_w * 0.42)
    chip_y = int(card_h * 0.22)
    chip_w = int(card_w * 0.14)
    chip_h = int(card_h * 0.18)
    card_draw.rounded_rectangle(
        [chip_x, chip_y, chip_x + chip_w, chip_y + chip_h],
        radius=6,
        fill=(245, 158, 11, 255),
        outline=(217, 119, 6, 255),
        width=2
    )
    card_draw.line([(chip_x, chip_y + chip_h // 2), (chip_x + chip_w, chip_y + chip_h // 2)], fill=(217, 119, 6, 255), width=2)
    card_draw.line([(chip_x + chip_w // 2, chip_y), (chip_x + chip_w // 2, chip_y + chip_h)], fill=(217, 119, 6, 255), width=2)

    # Data bars
    bar_x = int(card_w * 0.42)
    bars_y = [int(card_h * 0.48), int(card_h * 0.58), int(card_h * 0.68), int(card_h * 0.78)]
    bars_w = [int(card_w * 0.48), int(card_w * 0.36), int(card_w * 0.42), int(card_w * 0.28)]
    for by, bw in zip(bars_y, bars_w):
        card_draw.rounded_rectangle([bar_x, by, bar_x + bw, by + 12], radius=6, fill=(203, 213, 225, 255))

    # Watermarks
    try:
        font_sm = ImageFont.truetype("C:\\Windows\\Fonts\\msjhbd.ttc", 22)
        font_stamp = ImageFont.truetype("C:\\Windows\\Fonts\\msjhbd.ttc", 58)
        font_sub = ImageFont.truetype("C:\\Windows\\Fonts\\msjhbd.ttc", 24)
    except:
        font_sm = font_stamp = font_sub = ImageFont.load_default()

    wm_overlay = Image.new("RGBA", (card_w, card_h), (0, 0, 0, 0))

    tiled_text = "僅供使用 · 他用無效"
    tiled_layer = Image.new("RGBA", (card_w * 2, card_h * 2), (0, 0, 0, 0))
    tl_draw = ImageDraw.Draw(tiled_layer)
    for ty in range(0, card_h * 2, 70):
        for tx in range(0, card_w * 2, 260):
            tl_draw.text((tx, ty), tiled_text, font=font_sm, fill=(220, 38, 38, 38))
    tiled_rotated = tiled_layer.rotate(-22, resample=Image.BICUBIC, center=(card_w, card_h))
    wm_overlay.paste(tiled_rotated, (-card_w // 2, -card_h // 2), tiled_rotated)

    # Stamp
    stamp_layer = Image.new("RGBA", (card_w * 2, card_h * 2), (0, 0, 0, 0))
    sl_draw = ImageDraw.Draw(stamp_layer)
    scx = card_w
    scy = card_h
    stamp_w = 460
    stamp_h = 130
    s_left = scx - stamp_w // 2
    s_top = scy - stamp_h // 2
    
    sl_draw.rounded_rectangle([s_left, s_top, s_left + stamp_w, s_top + stamp_h], radius=16, outline=(220, 38, 38, 220), width=5)
    sl_draw.rounded_rectangle([s_left + 7, s_top + 7, s_left + stamp_w - 7, s_top + stamp_h - 7], radius=12, outline=(220, 38, 38, 160), width=2)
    sl_draw.rounded_rectangle([s_left + 8, s_top + 8, s_left + stamp_w - 8, s_top + stamp_h - 8], radius=11, fill=(254, 226, 226, 75))

    stamp_text1 = "僅 供 查 驗"
    stamp_text2 = "他 用 無 效  •  FOR VERIFY ONLY"
    bbox1 = font_stamp.getbbox(stamp_text1)
    tw1 = bbox1[2] - bbox1[0]
    sl_draw.text((scx - tw1 // 2, s_top + 16), stamp_text1, font=font_stamp, fill=(220, 38, 38, 235))
    
    bbox2 = font_sub.getbbox(stamp_text2)
    tw2 = bbox2[2] - bbox2[0]
    sl_draw.text((scx - tw2 // 2, s_top + 84), stamp_text2, font=font_sub, fill=(220, 38, 38, 220))

    stamp_rotated = stamp_layer.rotate(-20, resample=Image.BICUBIC, center=(scx, scy))
    wm_overlay.paste(stamp_rotated, (-card_w // 2, -card_h // 2), stamp_rotated)

    card.alpha_composite(wm_overlay)

    final_card = Image.new("RGBA", (card_w, card_h), (0, 0, 0, 0))
    final_card.paste(card, (0, 0), card_mask)
    canvas.alpha_composite(final_card, (card_x, card_y))

    # Badge
    seal_size = int(size * 0.32)
    seal_x = int(size * 0.60)
    seal_y = int(size * 0.58)

    seal_sh = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ssh_draw = ImageDraw.Draw(seal_sh)
    ssh_draw.ellipse([seal_x + 4, seal_y + 12, seal_x + seal_size + 4, seal_y + seal_size + 12], fill=(5, 10, 25, 150))
    seal_sh = seal_sh.filter(ImageFilter.GaussianBlur(14))
    canvas.alpha_composite(seal_sh)

    seal = Image.new("RGBA", (seal_size, seal_size), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(seal)
    for sy in range(seal_size):
        s_ratio = sy / float(seal_size)
        sr = int(16 * (1 - s_ratio) + 4 * s_ratio)
        sg = int(185 * (1 - s_ratio) + 120 * s_ratio)
        sb = int(129 * (1 - s_ratio) + 87 * s_ratio)
        s_draw.line([(0, sy), (seal_size, sy)], fill=(sr, sg, sb, 255))
        
    seal_mask = Image.new("L", (seal_size, seal_size), 0)
    sm_draw = ImageDraw.Draw(seal_mask)
    sm_draw.ellipse([0, 0, seal_size, seal_size], fill=255)
    
    s_draw.ellipse([0, 0, seal_size - 1, seal_size - 1], outline=(255, 255, 255, 240), width=6)
    s_draw.ellipse([8, 8, seal_size - 9, seal_size - 9], outline=(167, 243, 208, 140), width=2)

    sh_w = int(seal_size * 0.44)
    sh_h = int(seal_size * 0.52)
    sh_cx = seal_size // 2
    sh_cy = seal_size // 2

    shield_pts = [
        (sh_cx - sh_w // 2, sh_cy - sh_h // 2),
        (sh_cx + sh_w // 2, sh_cy - sh_h // 2),
        (sh_cx + sh_w // 2, sh_cy + int(sh_h * 0.1)),
        (sh_cx, sh_cy + sh_h // 2),
        (sh_cx - sh_w // 2, sh_cy + int(sh_h * 0.1)),
    ]
    s_draw.polygon(shield_pts, fill=(255, 255, 255, 255))

    check_pts = [
        (sh_cx - int(sh_w * 0.28), sh_cy - int(sh_h * 0.02)),
        (sh_cx - int(sh_w * 0.08), sh_cy + int(sh_h * 0.20)),
        (sh_cx + int(sh_w * 0.30), sh_cy - int(sh_h * 0.20))
    ]
    s_draw.line(check_pts, fill=(5, 150, 105, 255), width=9, joint="curve")

    final_seal = Image.new("RGBA", (seal_size, seal_size), (0, 0, 0, 0))
    final_seal.paste(seal, (0, 0), seal_mask)
    canvas.alpha_composite(final_seal, (seal_x, seal_y))

    # Scale to requested size
    return canvas.resize((target_w, target_h), Image.Resampling.LANCZOS)

def create_adaptive_foreground(size=432):
    """
    Android adaptive icon foreground has dimensions 108dp x 108dp.
    The safe zone is the center 72dp x 72dp (i.e. scale = 72/108 = 0.666).
    We place the card+badge inside the safe zone with slight padding so it never gets clipped.
    """
    fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    content_size = int(size * 0.72)
    card_img = create_card_with_badge(content_size, content_size)
    offset = (size - content_size) // 2
    fg.alpha_composite(card_img, (offset, offset))
    return fg

def create_full_icon(size=512, shape="squircle"):
    """
    Generates legacy icon with background squircle or circle.
    """
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    
    # Background gradient
    bg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg_draw = ImageDraw.Draw(bg)
    for y in range(size):
        ratio = y / float(size)
        r = int(30 * (1 - ratio) + 11 * ratio)
        g = int(58 * (1 - ratio) + 19 * ratio)
        b = int(138 * (1 - ratio) + 43 * ratio)
        bg_draw.line([(0, y), (size, y)], fill=(r, g, b, 255))
        
    highlight = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    hl_draw = ImageDraw.Draw(highlight)
    for rad in range(int(size * 0.7), 0, -8):
        alpha = int(45 * (1.0 - rad / (size * 0.7)))
        hl_draw.ellipse(
            [size * 0.5 - rad, size * 0.25 - rad * 0.7, size * 0.5 + rad, size * 0.25 + rad * 0.7],
            fill=(56, 189, 248, alpha)
        )
    highlight = highlight.filter(ImageFilter.GaussianBlur(int(size * 0.03)))
    bg.alpha_composite(highlight)

    # Mask
    mask = Image.new("L", (size, size), 0)
    m_draw = ImageDraw.Draw(mask)
    if shape == "circle":
        pad = int(size * 0.04)
        m_draw.ellipse([pad, pad, size - pad, size - pad], fill=255)
    else: # squircle
        pad = int(size * 0.04)
        rad = int(size * 0.22)
        m_draw.rounded_rectangle([pad, pad, size - pad, size - pad], radius=rad, fill=255)
        
    canvas.paste(bg, (0, 0), mask)
    
    # Place card content
    card_content = create_card_with_badge(size, size)
    canvas.alpha_composite(card_content, (0, 0))
    return canvas

def generate_all_android_assets(res_dir):
    densities = {
        "mdpi": {"legacy": 48, "foreground": 108},
        "hdpi": {"legacy": 72, "foreground": 162},
        "xhdpi": {"legacy": 96, "foreground": 216},
        "xxhdpi": {"legacy": 144, "foreground": 324},
        "xxxhdpi": {"legacy": 192, "foreground": 432},
    }
    
    for density, sizes in densities.items():
        folder = os.path.join(res_dir, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        
        # Legacy square/squircle icon
        sq_icon = create_full_icon(sizes["legacy"], shape="squircle")
        sq_icon.save(os.path.join(folder, "ic_launcher.png"), "PNG")
        
        # Legacy round icon
        rd_icon = create_full_icon(sizes["legacy"], shape="circle")
        rd_icon.save(os.path.join(folder, "ic_launcher_round.png"), "PNG")
        
        # Adaptive foreground
        fg_icon = create_adaptive_foreground(sizes["foreground"])
        fg_icon.save(os.path.join(folder, "ic_launcher_foreground.png"), "PNG")
        print(f"Generated assets for {density} ({sizes})")

    # Store Play Store icon 512x512
    play_icon = create_full_icon(512, shape="squircle")
    play_icon.save("play_store_512.png", "PNG")
    print("Generated play_store_512.png")

if __name__ == "__main__":
    res_dir = r"C:\Users\chuser\Documents\IdWatermark\app\src\main\res"
    generate_all_android_assets(res_dir)
