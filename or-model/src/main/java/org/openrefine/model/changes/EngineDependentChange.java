package org.openrefine.model.changes;

import org.openrefine.browsing.Engine;
import org.openrefine.browsing.EngineConfig;
import org.openrefine.model.GridState;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Change which respects any facets applied to the project.
 * 
 * @author Antonin Delpeuch
 */
public abstract class EngineDependentChange implements Change {
	
	protected final EngineConfig _engineConfig;
	
	public EngineDependentChange(EngineConfig engineConfig) {
		_engineConfig = engineConfig;
	}
	
	protected Engine getEngine(GridState grid) {
		return new Engine(grid, _engineConfig);
	}

	// For the changes that need serializing in JSON
	@JsonProperty("engineConfig")
	public EngineConfig getEngineConfig() {
		return _engineConfig;
	}

}
