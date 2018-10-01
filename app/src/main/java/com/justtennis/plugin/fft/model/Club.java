package com.justtennis.plugin.fft.model;

import com.justtennis.plugin.fft.model.generic.GenericDBPojoNamedSubId;


public class Club extends GenericDBPojoNamedSubId {
	
	private static final long serialVersionUID = 1L;

	public Club() {
		super();
	}

	public Club(Long id) {
		super(id);
	}

	public Club(Long id, String name) {
		super(id, name);
	}
}