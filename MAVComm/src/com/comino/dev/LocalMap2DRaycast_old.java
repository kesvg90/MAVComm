/****************************************************************************
 *
 *   Copyright (c) 2017 Eike Mansfeld ecm@gmx.de. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************/

package com.comino.dev;

import java.util.Arrays;

import com.comino.msp.model.DataModel;
import com.comino.msp.slam.map2D.ILocalMap;
import com.comino.msp.slam.map2D.filter.ILocalMapFilter;
import com.comino.msp.utils.MSPArrayUtils;

import boofcv.struct.image.GrayU16;
import georegression.struct.point.Point3D_F64;
import georegression.struct.point.Vector3D_F32;
import georegression.struct.point.Vector4D_F64;

public class LocalMap2DRaycast_old implements ILocalMap {


	private static final long OBLIVISION_TIME_MS = 500;
	private static final int  MAX_CERTAINITY     = 400;
	private static final int  CERTAINITY_INCR    = 20;

	private short[][] 		map;
	private short[][]    	window;

	private int 				cell_size_mm;
	private float			center_x_mm;
	private float			center_y_mm;

	private float			local_x_mm;
	private float			local_y_mm;

	private int 			    map_dimension;
	private int             window_dimension;
	private long            tms;

	private int				threshold = 0;

	public LocalMap2DRaycast_old() {
		this(40.0f,0.05f,2.0f,2);
	}

	public LocalMap2DRaycast_old(float diameter_m, float cell_size_m, float window_diameter_m, int threshold) {
		this(diameter_m, cell_size_m, window_diameter_m, diameter_m/2f, diameter_m/2f, threshold );
	}

	public LocalMap2DRaycast_old(float map_diameter_m, float cell_size_m, float window_diameter_m, float center_x_m, float center_y_m,  int threshold) {
		cell_size_mm = (int)(cell_size_m * 1000f);
		this.threshold = threshold;

		map_dimension  = (int)Math.floor(map_diameter_m / cell_size_m );
		map = new short[map_dimension][map_dimension];

		window_dimension = (int)Math.floor(window_diameter_m / cell_size_m );
		window = new short[window_dimension][window_dimension];

		reset();

		this.center_x_mm = center_x_m * 1000f;
		this.center_y_mm = center_y_m * 1000f;

		System.out.println("LocalMap2DRayCast initialized with "+map_dimension+"x"+map_dimension+" map and "+window.length+"x"+window.length+" window cells. ");
	}

	public void 	setLocalPosition(Vector3D_F32 point) {
		local_x_mm = point.x *1000f + center_x_mm;;
		local_y_mm = point.y *1000f + center_y_mm;;
	}

	public boolean update(Vector3D_F32 point) {
		return false;
	}

	public boolean update(Point3D_F64 point) {
		return false;
	}

	public boolean update(Point3D_F64 point, Vector4D_F64 pos) {
		return set(pos.x,pos.y, point.x,point.y,CERTAINITY_INCR) ;
	}


	public boolean update(float lpos_x, float lpos_y, Vector3D_F32 point) {
		return set(lpos_x,lpos_y, point.x,point.y,CERTAINITY_INCR) ;
	}

	public boolean update(float lpos_x, float lpos_y, Point3D_F64 point) {
		return set(lpos_x,lpos_y, point.x,point.y,CERTAINITY_INCR) ;
	}

	public boolean merge(LocalMap2DRaycast_old m, float weight) {
		return true;
	}

	public void processWindow(float lpos_x, float lpos_y) {

		int px,py, new_x,new_y;
		int center = window_dimension/2;

		px = (int)Math.floor( (lpos_x * 1000.0f + center_x_mm) / cell_size_mm);
		py = (int)Math.floor( (lpos_y * 1000.0f + center_y_mm) / cell_size_mm);


		for (int y = 0; y < window_dimension; y++) {
			for (int x = 0; x < window_dimension; x++) {

				new_x = x + px - center;
				new_y = y + py - center;

				if (new_x < map_dimension && new_y < map_dimension && new_x >= 0 && new_y >= 0)
					window[x][y] = map[new_x][new_y];
				else
					window[x][y] = Short.MAX_VALUE;
			}
		}
	}

	public int getWindowValue(int x, int y) {
		return window[x][y];
	}


	public float nearestDistance(float lpos_x, float lpos_y) {

		float distance = Float.MAX_VALUE, d;
		int center = window_dimension/2;

		for (int y = 0; y < window_dimension; y++) {
			for (int x = 0; x < window_dimension; x++) {
				if(window[x][y] <= threshold)
					continue;
				d = (float)Math.sqrt((x - center)*(x - center) + (y - center)*(y - center));
				if(d < distance)
					distance = d;
			}
		}
		return (distance * cell_size_mm + cell_size_mm/2) / 1000.0f;
	}

	public short get(float xpos, float ypos) {
		int x = (int)Math.floor((xpos*1000f+center_x_mm)/cell_size_mm);
		int y = (int)Math.floor((ypos*1000f+center_y_mm)/cell_size_mm);
		if(x >=0 && x < map[0].length && y >=0 && y < map[0].length)
			return map[x][y];
		return -1;
	}

	public boolean set(double xpos1, double ypos1, double xpos2, double ypos2, int value) {
		int x1 = (int)Math.floor((xpos1*1000f+center_x_mm)/cell_size_mm);
		int y1 = (int)Math.floor((ypos1*1000f+center_y_mm)/cell_size_mm);
		int x2 = (int)Math.floor((xpos2*1000f+center_x_mm)/cell_size_mm);
		int y2 = (int)Math.floor((ypos2*1000f+center_y_mm)/cell_size_mm);
		drawBresenhamLine(x1,y1,x2,y2,value);
		return true;
	}

	public void toDataModel(DataModel model,  boolean debug) {
		//TODO: Only transfer changes
		for (int y = 0; y <map_dimension; y++) {
			for (int x = 0; x < map_dimension; x++) {
				if(map[x][y] > threshold)
					model.grid.setBlock((x*cell_size_mm-center_x_mm)/1000f,(y*cell_size_mm-center_y_mm)/1000f, 0, true);
				else
					model.grid.setBlock((x*cell_size_mm-center_x_mm)/1000f,(y*cell_size_mm-center_y_mm)/1000f, 0, false);
			}
		}
		if(debug)
			System.out.println(model.grid);
	}

	public void forget() {
		if((System.currentTimeMillis()-tms)>OBLIVISION_TIME_MS) {
			tms = System.currentTimeMillis();
			for (int i = 0; i < map_dimension; ++i)
				for (int j = 0; j < map_dimension; ++j) {
					if(map[i][j] == 0)
						continue;
					map[i][j] -= CERTAINITY_INCR/4;
					if(map[i][j]<0) map[i][j] = 0;
				}
		}
	}

	public int getWindowDimension() {
		return window_dimension;
	}

	public int getMapDimension() {
		return map_dimension;
	}

	public int getCellSize_mm() {
		return cell_size_mm;
	}

	public void reset() {
		for (short[] row : map)
			Arrays.fill(row, (short)0);
		for (short[] row : window)
			Arrays.fill(row, (short)0);
	}

	public short[][] get() {
		return map;
	}

	@Override
	public GrayU16 getMap() {
		return MSPArrayUtils.convertToGrayU16(map, null);
	}

	private void drawBresenhamLine( int x1, int y1, int x2, int y2, int value)
	{
		int xIncrement = 1,
				yIncrement = 1,
				dy = 2*(y2-y1),
				dx = 2*(x1-x2),
				tmp;

		if ( x1 > x2 ) {      // Spiegeln an Y-Achse
			xIncrement = -1;
			dx = -dx;
		}

		if ( y1 > y2 ) {      // Spiegeln an X-Achse
			yIncrement= -1;
			dy= -dy;
		}

		int e = 2*dy + dx;
		int x = x1;           // Startpunkte setzen
		int y = y1;

		if ( dy < -dx )       // Steigung < 1
		{
			while( x != (x2+1) )
			{
				e += dy;
				if ( e > 0)
				{
					e += dx;
					y += yIncrement;
				}
				draw_into_map(x,y,0);
				x += xIncrement;
			}
		}
		else // ( dy >= -dx )   Steigung >=1
		{
			tmp = -dx;
			dx = -dy;
			dy = tmp;

			e = 2*dy + dx;

			while( y != (y2+1) )
			{
				e += dy;
				if( e > 0 ) {
					e += dx;
					x += xIncrement;
				}
				draw_into_map(x,y,0);
				y += yIncrement;
			}
		}
		draw_into_map(x,y,value);
	}

	private void draw_into_map(int xm, int ym, int value) {

		if (xm< 0 || xm >= map.length || ym < 0 || ym >= map.length)
			return;

		if(value > 0)
			set_map_point(xm,ym,value);
		else
			clear_map_point(xm,ym);

	}

	private boolean set_map_point(int x,int y, int dr) {
		if(x >=0 && y>=0 && x < map.length && y < map.length) {
			if(map[x][y]<MAX_CERTAINITY) {
				map[x][y] +=dr;
				return true;
			}
		}
		return false;
	}

	private boolean clear_map_point(int x,int y) {
		if(x >=0 && y>=0 && x < map.length && y < map.length) {
			map[x][y] = 0;
			return true;
		}
		return false;
	}

	public String toString() {
		StringBuilder b = new StringBuilder();
		for(int y=0; y<map_dimension; y++) {
			for(int x=0; x<map_dimension; x++) {
				if(Math.abs(local_x_mm - x * cell_size_mm)<cell_size_mm &&
						Math.abs(local_y_mm - y * cell_size_mm)<cell_size_mm)
					b.append("o ");
				else if(map[x][y]>0) {
					b.append("X ");
				}
				else
					b.append(". ");
			}
			b.append("\n");
		}
		b.append("\n");
		return b.toString();
	}

	public String windowToString() {
		StringBuilder b = new StringBuilder();
		for(int y=0; y<window.length; y++) {
			for(int x=0; x<window.length; x++) {
				if(window[x][y]>0) {
					b.append("X ");
				}
				else
					b.append(". ");
			}
			b.append("\n");
		}
		b.append("\n");
		return b.toString();
	}

	@Override
	public void applyMapFilter(ILocalMapFilter filter) {
		filter.apply(map);

	}

	public static void main(String[] args) {
		LocalMap2DRaycast_old map = new LocalMap2DRaycast_old(11,1,2, 1);
		System.out.println(map);

	}

	@Override
	public void setDataModel(DataModel model) {
		// TODO Auto-generated method stub

	}

	@Override
	public void toDataModel(boolean debug) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setIsLoaded(boolean loaded) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isLoaded() {
		// TODO Auto-generated method stub
		return false;
	}
}
