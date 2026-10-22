import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_app_icon(size=1024):
    # Create canvas with supersampling at 1024x1024
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 1. Background squircle / rounded rect
    bg_radius = int(size * 0.22)  # Android squircle curvature
    bg_box = [int(size * 0.04), int(size * 0.04), int(size * 0.96), int(size * 0.96)]
    
    # Generate background gradient
    bg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg_draw = ImageDraw.Draw(bg)
    
    # Draw gradient into bg
    for y in range(size):
        ratio = y / float(size)
        # Gradient from #1E3A8A (30, 58, 138) to #0B132B (11, 19, 43)
        r = int(30 * (1 - ratio) + 11 * ratio)
        g = int(58 * (1 - ratio) + 19 * ratio)
        b = int(138 * (1 - ratio) + 43 * ratio)
        bg_draw.line([(0, y), (size, y)], fill=(r, g, b, 255))
        
    # Subtle radial highlight on top-center
    highlight = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    hl_draw = ImageDraw.Draw(highlight)
    for rad in range(int(size * 0.7), 0, -8):
        alpha = int(45 * (1.0 - rad / (size * 0.7)))
        hl_draw.ellipse(
            [size * 0.5 - rad, size * 0.25 - rad * 0.7, size * 0.5 + rad, size * 0.25 + rad * 0.7],
            fill=(56, 189, 248, alpha)
        )
    highlight = highlight.filter(ImageFilter.GaussianBlur(25))
    bg.alpha_composite(highlight)

    # Mask background into rounded rectangle
    mask = Image.new("L", (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.rounded_rectangle(bg_box, radius=bg_radius, fill=255)
    
    # Composite background
    final_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    final_img.paste(bg, (0, 0), mask)

    # 2. Shadow for ID Card
    card_w = int(size * 0.74)
    card_h = int(size * 0.48)
    card_x = int((size - card_w) / 2)
    card_y = int(size * 0.22)
    card_r = int(size * 0.04)

    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sh_draw = ImageDraw.Draw(shadow)
    sh_draw.rounded_rectangle(
        [card_x, card_y + 16, card_x + card_w, card_y + card_h + 24],
        radius=card_r,
        fill=(5, 10, 25, 140)
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    final_img.alpha_composite(shadow)

    # 3. ID Card Base
    card = Image.new("RGBA", (card_w, card_h), (0, 0, 0, 0))
    card_draw = ImageDraw.Draw(card)
    
    # Card surface gradient (crisp clean white to pearl grey)
    for cy in range(card_h):
        c_ratio = cy / float(card_h)
        c_val = int(255 * (1 - c_ratio) + 241 * c_ratio)
        card_draw.line([(0, cy), (card_w, cy)], fill=(c_val, c_val + 2, c_val + 5, 255))
        
    # Card mask
    card_mask = Image.new("L", (card_w, card_h), 0)
    card_mask_draw = ImageDraw.Draw(card_mask)
    card_mask_draw.rounded_rectangle([0, 0, card_w, card_h], radius=card_r, fill=255)

    # Top decorative security ribbon on card
    ribbon_h = int(card_h * 0.12)
    ribbon = Image.new("RGBA", (card_w, ribbon_h), (0, 0, 0, 0))
    ribbon_draw = ImageDraw.Draw(ribbon)
    for rx in range(card_w):
        r_ratio = rx / float(card_w)
        # Cyan to indigo gradient #06B6D4 -> #6366F1
        rr = int(6 * (1 - r_ratio) + 99 * r_ratio)
        rg = int(182 * (1 - r_ratio) + 102 * r_ratio)
        rb = int(212 * (1 - r_ratio) + 241 * r_ratio)
        ribbon_draw.line([(rx, 0), (rx, ribbon_h)], fill=(rr, rg, rb, 255))
    card.paste(ribbon, (0, 0))

    # Fine security border on card
    card_draw.rounded_rectangle([0, 0, card_w - 1, card_h - 1], radius=card_r, outline=(226, 232, 240, 255), width=2)

    # 4. Card Content
    # Left: Photo Box
    photo_w = int(card_w * 0.28)
    photo_h = int(card_h * 0.60)
    photo_x = int(card_w * 0.08)
    photo_y = int(card_h * 0.22)
    
    # Photo background
    card_draw.rounded_rectangle(
        [photo_x, photo_y, photo_x + photo_w, photo_y + photo_h],
        radius=14,
        fill=(226, 232, 240, 255),
        outline=(203, 213, 225, 255),
        width=2
    )
    
    # Minimalist Avatar silhouette inside photo box
    av_cx = photo_x + photo_w // 2
    av_cy = photo_y + int(photo_h * 0.40)
    head_rad = int(photo_w * 0.22)
    card_draw.ellipse(
        [av_cx - head_rad, av_cy - head_rad, av_cx + head_rad, av_cy + head_rad],
        fill=(148, 163, 184, 255)
    )
    # Avatar shoulders arc
    card_draw.chord(
        [av_cx - int(photo_w * 0.38), av_cy + int(head_rad * 0.8),
         av_cx + int(photo_w * 0.38), photo_y + photo_h + 10],
        start=180, end=0,
        fill=(148, 163, 184, 255)
    )

    # Right: Golden Security Chip
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
    # Chip circuit line
    card_draw.line(
        [(chip_x, chip_y + chip_h // 2), (chip_x + chip_w, chip_y + chip_h // 2)],
        fill=(217, 119, 6, 255), width=2
    )
    card_draw.line(
        [(chip_x + chip_w // 2, chip_y), (chip_x + chip_w // 2, chip_y + chip_h)],
        fill=(217, 119, 6, 255), width=2
    )

    # Right: Data bars (simulating ID text fields)
    bar_x = int(card_w * 0.42)
    bars_y = [int(card_h * 0.48), int(card_h * 0.58), int(card_h * 0.68), int(card_h * 0.78)]
    bars_w = [int(card_w * 0.48), int(card_w * 0.36), int(card_w * 0.42), int(card_w * 0.28)]
    for by, bw in zip(bars_y, bars_w):
        card_draw.rounded_rectangle([bar_x, by, bar_x + bw, by + 12], radius=6, fill=(203, 213, 225, 255))

    # 5. Background Watermark Pattern on the Card (subtle repeating diagonal watermark)
    try:
        font_sm = ImageFont.truetype("C:\\Windows\\Fonts\\msjhbd.ttc", 22)
        font_stamp = ImageFont.truetype("C:\\Windows\\Fonts\\msjhbd.ttc", 58)
        font_sub = ImageFont.truetype("C:\\Windows\\Fonts\\msjhbd.ttc", 24)
    except:
        font_sm = font_stamp = font_sub = ImageFont.load_default()

    wm_overlay = Image.new("RGBA", (card_w, card_h), (0, 0, 0, 0))
    wm_draw = ImageDraw.Draw(wm_overlay)

    # Repeating subtle watermarks
    tiled_text = "僅供使用 · 他用無效"
    # We draw on a larger rotated layer then clip
    tiled_layer = Image.new("RGBA", (card_w * 2, card_h * 2), (0, 0, 0, 0))
    tl_draw = ImageDraw.Draw(tiled_layer)
    for ty in range(0, card_h * 2, 70):
        for tx in range(0, card_w * 2, 260):
            tl_draw.text((tx, ty), tiled_text, font=font_sm, fill=(220, 38, 38, 38))
    tiled_rotated = tiled_layer.rotate(-22, resample=Image.BICUBIC, center=(card_w, card_h))
    wm_overlay.paste(tiled_rotated, (-card_w // 2, -card_h // 2), tiled_rotated)

    # 6. Main Bold Red Security Watermark Stamp
    # Stamp banner angled at -20 degrees across card
    stamp_layer = Image.new("RGBA", (card_w * 2, card_h * 2), (0, 0, 0, 0))
    sl_draw = ImageDraw.Draw(stamp_layer)
    
    scx = card_w
    scy = card_h
    stamp_w = 460
    stamp_h = 130
    s_left = scx - stamp_w // 2
    s_top = scy - stamp_h // 2
    
    # Stamp outer double border (classic official watermark stamp)
    sl_draw.rounded_rectangle(
        [s_left, s_top, s_left + stamp_w, s_top + stamp_h],
        radius=16,
        outline=(220, 38, 38, 220),
        width=5
    )
    sl_draw.rounded_rectangle(
        [s_left + 7, s_top + 7, s_left + stamp_w - 7, s_top + stamp_h - 7],
        radius=12,
        outline=(220, 38, 38, 160),
        width=2
    )
    
    # Stamp inner fill tint (semi-transparent red wash)
    sl_draw.rounded_rectangle(
        [s_left + 8, s_top + 8, s_left + stamp_w - 8, s_top + stamp_h - 8],
        radius=11,
        fill=(254, 226, 226, 75)
    )

    # Stamp Text
    stamp_text1 = "僅 供 查 驗"
    stamp_text2 = "他 用 無 效  •  FOR VERIFY ONLY"
    
    # Center text
    bbox1 = font_stamp.getbbox(stamp_text1)
    tw1 = bbox1[2] - bbox1[0]
    th1 = bbox1[3] - bbox1[1]
    sl_draw.text((scx - tw1 // 2, s_top + 16), stamp_text1, font=font_stamp, fill=(220, 38, 38, 235))
    
    bbox2 = font_sub.getbbox(stamp_text2)
    tw2 = bbox2[2] - bbox2[0]
    sl_draw.text((scx - tw2 // 2, s_top + 84), stamp_text2, font=font_sub, fill=(220, 38, 38, 220))

    # Rotate stamp
    stamp_rotated = stamp_layer.rotate(-20, resample=Image.BICUBIC, center=(scx, scy))
    wm_overlay.paste(stamp_rotated, (-card_w // 2, -card_h // 2), stamp_rotated)

    # Apply watermark to card
    card.alpha_composite(wm_overlay)

    # Paste card onto final canvas using card_mask
    final_card = Image.new("RGBA", (card_w, card_h), (0, 0, 0, 0))
    final_card.paste(card, (0, 0), card_mask)
    final_img.alpha_composite(final_card, (card_x, card_y))

    # 7. Privacy & Security Shield Seal (Bottom Right floating emblem)
    seal_size = int(size * 0.32)
    seal_x = int(size * 0.60)
    seal_y = int(size * 0.58)

    # Seal shadow
    seal_sh = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ssh_draw = ImageDraw.Draw(seal_sh)
    ssh_draw.ellipse(
        [seal_x + 4, seal_y + 12, seal_x + seal_size + 4, seal_y + seal_size + 12],
        fill=(5, 10, 25, 150)
    )
    seal_sh = seal_sh.filter(ImageFilter.GaussianBlur(14))
    final_img.alpha_composite(seal_sh)

    # Seal circle gradient (Emerald gradient #10B981 -> #047857)
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
    
    # Outer white ring on seal
    s_draw.ellipse([0, 0, seal_size - 1, seal_size - 1], outline=(255, 255, 255, 240), width=6)
    s_draw.ellipse([8, 8, seal_size - 9, seal_size - 9], outline=(167, 243, 208, 140), width=2)

    # Modern Shield Icon inside seal
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

    # Padlock / Checkmark inside shield (in emerald green)
    # Checkmark coords
    check_pts = [
        (sh_cx - int(sh_w * 0.28), sh_cy - int(sh_h * 0.02)),
        (sh_cx - int(sh_w * 0.08), sh_cy + int(sh_h * 0.20)),
        (sh_cx + int(sh_w * 0.30), sh_cy - int(sh_h * 0.20))
    ]
    s_draw.line(check_pts, fill=(5, 150, 105, 255), width=9, joint="curve")

    # Composite seal
    final_seal = Image.new("RGBA", (seal_size, seal_size), (0, 0, 0, 0))
    final_seal.paste(seal, (0, 0), seal_mask)
    final_img.alpha_composite(final_seal, (seal_x, seal_y))

    # Return final image
    return final_img

if __name__ == "__main__":
    icon = create_app_icon(1024)
    icon.save("icon_master_1024.png", "PNG")
    icon_512 = icon.resize((512, 512), Image.Resampling.LANCZOS)
    icon_512.save("icon_master_512.png", "PNG")
    print("Icons generated successfully!")
