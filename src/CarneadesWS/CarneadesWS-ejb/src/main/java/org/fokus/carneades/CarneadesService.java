/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.fokus.carneades;

import java.util.List;
import javax.ejb.Remote;
import org.fokus.carneades.api.CarneadesMessage;
import org.fokus.carneades.api.Statement;

/**
 *
 * @author stb
 */

@Remote
public interface CarneadesService {

    CarneadesMessage askEngine(Statement query, String kb, List<Statement> answers);

}
