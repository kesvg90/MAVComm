package com.comino.mav.mavlink.plugins;

import org.mavlink.messages.lquac.msg_msp_vision;

import com.comino.msp.model.segment.Status;

public class MSPVisionPlugin extends MAVLinkPluginBase {

	public MSPVisionPlugin() {
		super(msg_msp_vision.class);
	}

	@Override
	public void received(Object o) {

		msg_msp_vision mocap = (msg_msp_vision) o;
		model.vision.vx = mocap.vx;
		model.vision.vy = mocap.vy;
		model.vision.vz = mocap.vz;

		// model.vision.x = mocap.x;
		// model.vision.y = mocap.y;
		// model.vision.z = mocap.z;
		//
		// model.vision.h= mocap.h;
		// model.vision.p= mocap.p;
		// model.vision.r= mocap.r;

		model.vision.qual = mocap.quality;
		model.vision.errors = (int) mocap.errors;

		model.vision.flags = (int) mocap.flags;
		model.vision.fps = mocap.fps;
		if (model.vision.errors < 5 && ( mocap.vx !=0 || mocap.vy!=0)) {
			model.vision.tms = model.sys.getSynchronizedPX4Time_us();
			model.sys.setSensor(Status.MSP_OPCV_AVAILABILITY, true);
		}
	}
}
