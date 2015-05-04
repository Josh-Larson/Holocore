/***********************************************************************************
* Copyright (c) 2015 /// Project SWG /// www.projectswg.com                        *
*                                                                                  *
* ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on           *
* July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies.  *
* Our goal is to create an emulator which will provide a server for players to     *
* continue playing a game similar to the one they used to play. We are basing      *
* it on the final publish of the game prior to end-game events.                    *
*                                                                                  *
* This file is part of Holocore.                                                   *
*                                                                                  *
* -------------------------------------------------------------------------------- *
*                                                                                  *
* Holocore is free software: you can redistribute it and/or modify                 *
* it under the terms of the GNU Affero General Public License as                   *
* published by the Free Software Foundation, either version 3 of the               *
* License, or (at your option) any later version.                                  *
*                                                                                  *
* Holocore is distributed in the hope that it will be useful,                      *
* but WITHOUT ANY WARRANTY; without even the implied warranty of                   *
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                    *
* GNU Affero General Public License for more details.                              *
*                                                                                  *
* You should have received a copy of the GNU Affero General Public License         *
* along with Holocore.  If not, see <http://www.gnu.org/licenses/>.                *
*                                                                                  *
***********************************************************************************/
package resources.encodables.player;

import resources.network.BaselineBuilder.Encodable;

public class Mail implements Encodable {
	private static final long serialVersionUID = 1L;

	private int id;
	private String sender;
	private long receiverId;
	private String subject;
	private String message;
	private byte status;
	private int timestamp;
	// TODO: Waypoint attachments
	
	public static final byte NEW = 0x4E;
	public static final byte READ = 0x52;
	public static final byte UNREAD = 0x55;
	
	public Mail(String sender, String subject, String message, long receiverId) {
		this.sender = sender;
		this.subject = subject;
		this.message = message;
		this.receiverId = receiverId;
		this.status = NEW;
	}
	
	@Override
	public byte [] encode() {
		// TODO Auto-generated method stub
		return new byte[0];
	}

	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}

	public String getSender() {
		return sender;
	}

	public long getReceiverId() {
		return receiverId;
	}

	public String getSubject() {
		return subject;
	}

	public String getMessage() {
		return message;
	}

	public byte getStatus() {
		return status;
	}

	public void setStatus(byte status) {
		this.status = status;
	}

	public int getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(int timestamp) {
		this.timestamp = timestamp;
	}
}
