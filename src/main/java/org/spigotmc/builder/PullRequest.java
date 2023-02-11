package org.spigotmc.builder;

import java.util.Locale;
import joptsimple.ValueConversionException;
import joptsimple.ValueConverter;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PullRequest
{

    private Repository repository;
    private int id;

    public static class PullRequestConverter implements ValueConverter<PullRequest>
    {

        @Override
        public PullRequest convert(String value)
        {
            String[] values = value.split( ":" );

            if ( values.length != 2 )
            {
                throw new ValueConversionException( "Expected one ':' but got " + ( value.length() - 1 ) );
            }

            Repository repository;
            try
            {
                repository = Repository.valueOf( values[0].toUpperCase( Locale.ROOT ) );
            } catch ( Exception e )
            {
                throw new ValueConversionException( "Cannot parse " + values[0] + " to a repository", e );
            }

            int id;
            try
            {
                id = Integer.parseInt( values[1] );
            } catch ( Exception e )
            {
                throw new ValueConversionException( "Cannot parse " + values[1] + " to an integer", e );
            }

            return new PullRequest( repository, id );
        }

        @Override
        public Class<? extends PullRequest> valueType()
        {
            return PullRequest.class;
        }

        @Override
        public String valuePattern()
        {
            return "<repo>:<id>";
        }
    }
}
