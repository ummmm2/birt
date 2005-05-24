/*******************************************************************************
 * Copyright (c) 2004 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.designer.internal.ui.editors.schematic.border;

import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.swt.SWT;

/**
 * Utility class for border drawing.
 */

public class BorderUtil
{

	/**
	 * Position constant for Top border.
	 */
	public static final int TOP = 0;
	/**
	 * Position constant for Bottom border.
	 */
	public static final int BOTTOM = 1;
	/**
	 * Position constant for Left border.
	 */
	public static final int LEFT = 2;
	/**
	 * Position constant for Right border.
	 */
	public static final int RIGHT = 3;

	/**
	 * Width constant for default border line.
	 */
	private static final int[] DEFAULT_LINE_WIDTH = new int[]{
			1, 1, 1, 1
	};

	/**
	 * Calculate gap to avoid cross-line when drawing thick/double line.
	 */
	private static int getGap( int x, int y, int i )
	{
		if ( x == 0 )
		{
			return 0;
		}

		if ( i < 0 )
		{
			i = 0;
		}

		if ( i > x )
		{
			i = x;
		}

		return  y * i / x ;
	}

	/**
	 * Draws a double style line.
	 * 
	 * @param figure
	 * @param g
	 * @param side
	 * @param width
	 *            the border width array, arranged by {top, bottom, left,
	 *            right};
	 * @param r
	 */
	public static void drawDoubleLine( Graphics g, int side, int[] width,
			Rectangle r )
	{
		/**
		 * calculate the line width and the blank width. the line/blank width is
		 * the average of the original width. to ensure all space is used.if the
		 * remainder is 1, assign it to the blank, if 2, assign it to the line.
		 */
		int lineWidth, blankWidth;
		lineWidth = blankWidth = width[side] / 3;

		if ( width[side] % 3 == 1 )
		{
			blankWidth++;
		}
		else if ( width[side] % 3 == 2 )
		{
			lineWidth++;
		}

		/**
		 * Tweak if whole width is 1, this cause the line width be Zero, force
		 * it to show 1px line. This behavior is like IE, but according to CSS
		 * spec, should draw nothing like Mozilla do.
		 */
		if ( lineWidth == 0 && width[side] != 0 )
		{
			drawSingleLine( g, side, SWT.LINE_SOLID, width, 1, 0, r );

			return;
		}

		//draw the first line.
		drawSingleLine( g, side, SWT.LINE_SOLID, width, lineWidth, 0, r );

		//draw the second line.
		drawSingleLine( g, side, SWT.LINE_SOLID, width, lineWidth, lineWidth
				+ blankWidth, r );
	}

	/**
	 * Draws a default grayed line.
	 * 
	 * @param figure
	 * @param g
	 * @param side
	 * @param r
	 */
	public static void drawDefaultLine( Graphics g, int side, Rectangle r )
	{
		drawSingleLine( g, side, SWT.LINE_SOLID, DEFAULT_LINE_WIDTH, r );
	}

	/**
	 * Convenient version, set actualWidth=-1, startPos=0.
	 * 
	 * @param figure
	 * @param g
	 * @param side
	 * @param style
	 * @param width
	 * @param r
	 */
	public static void drawSingleLine( Graphics g, int side, int style,
			int[] width, Rectangle r )
	{
		drawSingleLine( g, side, style, width, -1, 0, r );
	}

	/**
	 * Draws a single style line.
	 * 
	 * @param figure
	 * @param g
	 * @param side
	 * @param style
	 * @param width
	 *            the border width array, arranged by {top, bottom, left,
	 *            right};
	 * @param actualWidth
	 *            if this value is greater or equal to Zero, use this to draw
	 *            the line, or will use the specified width array value.
	 * @param startPos
	 *            indicate the drawing start position.
	 * @param r
	 */
	private static void drawSingleLine( Graphics g, int side, int style,
			int[] width, int actualWidth, int startPos, Rectangle r )
	{
		g.setLineStyle( style );

		Rectangle oldClip = g.getClip( new Rectangle( ) );

		Rectangle clip = new Rectangle( );
		Point p2 = new Point( );

		switch ( side )
		{
			case BOTTOM :
				if ( actualWidth < 0 )
				{
					actualWidth = width[side];
				}
				for ( int i = 0; i < actualWidth; i++ )
				{
					clip.width = 0;
					clip.height = 0;
					clip.x = r.x
							+ getGap( width[BOTTOM], width[LEFT], i + startPos );
					clip.y = r.y + r.height - i - startPos - 1;
					p2.x = r.x
							+ r.width
							- getGap( width[BOTTOM], width[RIGHT], i + startPos )
							- 1;
					p2.y = r.y + r.height - i - startPos - 1;
					clip.union( p2 );
					g.clipRect( clip );

					g.drawLine( r.x, r.y + r.height - i - startPos - 1, r.x
							+ r.width, r.y + r.height - i - startPos - 1 );
					g.setClip( oldClip );
				}
				break;
			case TOP :
				if ( actualWidth < 0 )
				{
					actualWidth = width[side];
				}
				for ( int i = 0; i < actualWidth; i++ )
				{
					clip.width = 0;
					clip.height = 0;
					clip.x = r.x
							+ getGap( width[TOP], width[LEFT], i + startPos );
					clip.y = r.y + i + startPos;
					p2.x = r.x
							+ r.width
							- getGap( width[TOP], width[RIGHT], i + startPos )
							- 1;
					p2.y = r.y + i + startPos;
					clip.union( p2 );
					g.clipRect( clip );

					g.drawLine( r.x, r.y + i + startPos, r.x + r.width, r.y
							+ i
							+ startPos );
					g.setClip( oldClip );
				}
				break;
			case LEFT :
				if ( actualWidth < 0 )
				{
					actualWidth = width[side];
				}
				for ( int i = 0; i < actualWidth; i++ )
				{
					clip.width = 0;
					clip.height = 0;
					clip.x = r.x + i + startPos;
					clip.y = r.y
							+ getGap( width[LEFT], width[TOP], i + startPos );
					p2.x = r.x + i + startPos;
					p2.y = r.y
							+ r.height
							- getGap( width[LEFT], width[BOTTOM], i + startPos )
							- 1;
					clip.union( p2 );
					g.clipRect( clip );

					g.drawLine( r.x + i + startPos,
							r.y,
							r.x + i + startPos,
							r.y + r.height );
					g.setClip( oldClip );
				}
				break;
			case RIGHT :
				if ( actualWidth < 0 )
				{
					actualWidth = width[side];
				}
				for ( int i = 0; i < actualWidth; i++ )
				{
					clip.width = 0;
					clip.height = 0;
					clip.x = r.x + r.width - i - startPos - 1;
					clip.y = r.y
							+ getGap( width[RIGHT], width[TOP], i + startPos );
					p2.x = r.x + r.width - i - startPos - 1;
					p2.y = r.y
							+ r.height
							- getGap( width[RIGHT], width[BOTTOM], i + startPos )
							- 1;
					clip.union( p2 );
					g.clipRect( clip );

					g.drawLine( r.x + r.width - i - startPos - 1, r.y, r.x
							+ r.width
							- i
							- startPos
							- 1, r.y + r.height );
					g.setClip( oldClip );
				}
				break;
		}

		g.setClip( oldClip );
	}

}