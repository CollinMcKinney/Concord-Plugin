package com.Concord;

import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

public class ConcordPanel extends PluginPanel
{
    private static final int DISCORD_BUTTON_MAX_WIDTH = 140;
    private static final int DISCORD_BUTTON_MAX_HEIGHT = 48;

    private static final Color CLAN_COLOR_PRIMARY = new Color(117, 170, 0);
    private static final Color CLAN_COLOR_SECONDARY = new Color(64, 64, 64);
    private static final Color PANEL_BACKGROUND = new Color(20, 20, 20);

    private JButton joinDiscordButton;
    private String discordInviteUrl = "";

    public void init()
    {
        setLayout(new BorderLayout());
        setBackground(PANEL_BACKGROUND);
        setForeground(CLAN_COLOR_PRIMARY);
        setBorder(new EmptyBorder(20, 16, 20, 16));

        buildPanel();
    }

    private void buildPanel()
    {
        removeAll();
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(PANEL_BACKGROUND);
        contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        BufferedImage logoImage = ImageUtil.loadImageResource(getClass(), "/icon_128x128.png");
        JLabel logoLabel = new JLabel(new ImageIcon(logoImage));
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLabel = new JLabel("Scape Society");
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setForeground(CLAN_COLOR_PRIMARY);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));

        JLabel subtitleLabel = new JLabel("Join the clan Discord community");
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitleLabel.setForeground(new Color(205, 214, 190));

        BufferedImage discordButtonImage = ImageUtil.loadImageResource(getClass(), "/join_our_discord.png");
        Image scaledDiscordButtonImage = scaleImageToFit(
                discordButtonImage,
                DISCORD_BUTTON_MAX_WIDTH,
                DISCORD_BUTTON_MAX_HEIGHT
        );
        Image hoveredDiscordButtonImage = darkenImage(scaledDiscordButtonImage, 0.82f);
        joinDiscordButton = new JButton(new ImageIcon(scaledDiscordButtonImage));
        joinDiscordButton.setToolTipText("Join Our Discord");
        joinDiscordButton.setBorderPainted(false);
        joinDiscordButton.setContentAreaFilled(false);
        joinDiscordButton.setFocusPainted(false);
        joinDiscordButton.setOpaque(false);
        joinDiscordButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        joinDiscordButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        joinDiscordButton.setPreferredSize(new Dimension(scaledDiscordButtonImage.getWidth(null), scaledDiscordButtonImage.getHeight(null)));
        joinDiscordButton.setMaximumSize(joinDiscordButton.getPreferredSize());
        joinDiscordButton.addActionListener(e -> openDiscordInvite());
        joinDiscordButton.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                joinDiscordButton.setIcon(new ImageIcon(hoveredDiscordButtonImage));
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                joinDiscordButton.setIcon(new ImageIcon(scaledDiscordButtonImage));
            }
        });

        contentPanel.add(Box.createVerticalGlue());
        contentPanel.add(logoLabel);
        contentPanel.add(Box.createVerticalStrut(16));
        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(6));
        contentPanel.add(subtitleLabel);
        contentPanel.add(Box.createVerticalStrut(18));
        contentPanel.add(joinDiscordButton);
        contentPanel.add(Box.createVerticalGlue());

        add(contentPanel, BorderLayout.CENTER);

        revalidate();
        repaint();
    }

    private void openDiscordInvite()
    {
        if (discordInviteUrl == null || discordInviteUrl.trim().isEmpty())
        {
            JOptionPane.showMessageDialog(this, "Discord invite URL is not configured yet.");
            return;
        }

        try
        {
            Desktop.getDesktop().browse(new URI(discordInviteUrl));
        }
        catch (Exception ex)
        {
            JOptionPane.showMessageDialog(this, "Failed to open Discord: " + ex.getMessage());
        }
    }

    private Image scaleImageToFit(BufferedImage image, int maxWidth, int maxHeight)
    {
        if (image == null)
        {
            return image;
        }

        double widthScale = (double) maxWidth / image.getWidth();
        double heightScale = (double) maxHeight / image.getHeight();
        double scale = Math.min(1.0d, Math.min(widthScale, heightScale));

        int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
        return image.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
    }

    private Image darkenImage(Image image, float factor)
    {
        if (image == null)
        {
            return null;
        }

        int width = image.getWidth(null);
        int height = image.getHeight(null);
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = bufferedImage.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();

        BufferedImage darkenedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                int rgba = bufferedImage.getRGB(x, y);
                int alpha = (rgba >>> 24) & 0xFF;
                int red = (rgba >>> 16) & 0xFF;
                int green = (rgba >>> 8) & 0xFF;
                int blue = rgba & 0xFF;

                red = Math.max(0, Math.min(255, Math.round(red * factor)));
                green = Math.max(0, Math.min(255, Math.round(green * factor)));
                blue = Math.max(0, Math.min(255, Math.round(blue * factor)));

                darkenedImage.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }

        return darkenedImage;
    }

    public void setDiscordInviteUrl(String discordInviteUrl)
    {
        this.discordInviteUrl = discordInviteUrl == null ? "" : discordInviteUrl.trim();
        if (joinDiscordButton != null)
        {
            joinDiscordButton.setEnabled(!this.discordInviteUrl.isEmpty());
            joinDiscordButton.setToolTipText(this.discordInviteUrl.isEmpty()
                    ? "Discord invite is not configured yet"
                    : "Join Our Discord");
        }
    }
}
